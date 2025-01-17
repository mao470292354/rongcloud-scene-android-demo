package cn.rongcloud.radio.helper;

import android.text.TextUtils;
import android.util.Log;

import com.basis.net.oklib.OkApi;
import com.basis.net.oklib.OkParams;
import com.basis.net.oklib.WrapperCallBack;
import com.basis.net.oklib.wrapper.Wrapper;
import com.kit.UIKit;
import com.kit.utils.KToast;
import com.kit.utils.Logger;
import com.rongcloud.common.utils.AccountStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cn.rong.combusis.api.VRApi;
import cn.rong.combusis.common.utils.JsonUtils;
import cn.rong.combusis.manager.AllBroadcastManager;
import cn.rong.combusis.message.RCAllBroadcastMessage;
import cn.rong.combusis.message.RCChatroomAdmin;
import cn.rong.combusis.message.RCChatroomBarrage;
import cn.rong.combusis.message.RCChatroomEnter;
import cn.rong.combusis.message.RCChatroomGift;
import cn.rong.combusis.message.RCChatroomGiftAll;
import cn.rong.combusis.message.RCChatroomKickOut;
import cn.rong.combusis.message.RCChatroomLeave;
import cn.rong.combusis.message.RCChatroomLocationMessage;
import cn.rong.combusis.message.RCChatroomVoice;
import cn.rong.combusis.message.RCFollowMsg;
import cn.rong.combusis.message.RCRRCloseMessage;
import cn.rong.combusis.music.MusicManager;
import cn.rong.combusis.widget.miniroom.MiniRoomManager;
import cn.rong.combusis.widget.miniroom.OnCloseMiniRoomListener;
import cn.rong.combusis.widget.miniroom.OnMiniRoomListener;
import cn.rongcloud.messager.RCMessager;
import cn.rongcloud.messager.SendMessageCallback;
import cn.rongcloud.radioroom.IRCRadioRoomEngine;
import cn.rongcloud.radioroom.RCRadioRoomEngine;
import cn.rongcloud.radioroom.callback.RCRadioRoomCallback;
import cn.rongcloud.radioroom.room.RCRadioEventListener;
import io.rong.imkit.picture.tools.ToastUtils;
import io.rong.imlib.model.Conversation;
import io.rong.imlib.model.Message;
import io.rong.imlib.model.MessageContent;
import io.rong.message.TextMessage;

/**
 * @author gyn
 * @date 2021/10/13
 * <p>
 * 维护一个监听的单例，方便房间最小化后状态的监听
 */
public class RadioEventHelper implements IRadioEventHelper, RCRadioEventListener, OnCloseMiniRoomListener {

    // 是否发送了默认消息
    private boolean isSendDefaultMessage = false;
    private List<RadioRoomListener> listeners = new ArrayList<>();
    private List<Message> messages = new ArrayList<>();
    private OnMiniRoomListener onMiniRoomListener;
    private String roomId;
    // 是否在麦位上
    private boolean isInSeat = false;
    // 是否暂停
    private boolean isSuspend = false;
    // 是否静音
    private boolean isMute = false;

    public static RadioEventHelper getInstance() {
        return Holder.INSTANCE;
    }

    public void setSendDefaultMessage(boolean sendDefaultMessage) {
        isSendDefaultMessage = sendDefaultMessage;
    }

    public String getRoomId() {
        return roomId;
    }

    public boolean isInSeat() {
        return isInSeat;
    }

    public void setInSeat(boolean inSeat) {
        isInSeat = inSeat;
    }

    public boolean isSuspend() {
        return isSuspend;
    }

    public void setSuspend(boolean suspend) {
        isSuspend = suspend;
    }

    public boolean isMute() {
        return isMute;
    }

    public void setMute(boolean mute) {
        isMute = mute;
    }

    @Override
    public void unRegister() {
        this.roomId = null;
        listeners.clear();
        messages.clear();
        isInSeat = false;
        isSuspend = false;
        isMute = false;
        isSendDefaultMessage = false;
        onMiniRoomListener = null;
    }

    @Override
    public void register(String roomId) {
        if (!TextUtils.equals(roomId, this.roomId)) {
            this.roomId = roomId;
            RCRadioRoomEngine.getInstance().setRadioEventListener(this);
        }
    }

    @Override
    public void onMessageReceived(Message message) {
        if (message.getConversationType() != Conversation.ConversationType.CHATROOM) {
            return;
        }
        MessageContent content = message.getContent();
        Logger.d("==============onMessageReceived: " + content.getClass() + JsonUtils.toJson(content));
        // 全局广播的消息
        if (content instanceof RCAllBroadcastMessage) {
            AllBroadcastManager.getInstance().addMessage((RCAllBroadcastMessage) content);
            return;
        }
        // 缓存消息
        if (isShowingMessage(message)) {
            messages.add(message);
        }

        if (content instanceof RCChatroomKickOut) {
            // 如果踢出的是自己，就离开房间
            String targetId = ((RCChatroomKickOut) content).getTargetId();
            if (TextUtils.equals(targetId, AccountStore.INSTANCE.getUserId())) {
                // 最小化后主动离开房间
                if (MiniRoomManager.getInstance().isShowing()) {
                    leaveRoom(new LeaveRoomCallback() {
                        @Override
                        public void leaveFinish() {
                            KToast.show("你已被踢出房间");
                            MiniRoomManager.getInstance().close();
                        }
                    });
                }
            }
        } else if (content instanceof RCRRCloseMessage) {
            // 最小化后关闭房间
            if (MiniRoomManager.getInstance().isShowing()) {
                KToast.show("您所在的房间已关闭");
            }
        }

        for (RCRadioEventListener l : listeners) {
            l.onMessageReceived(message);
        }
    }

    @Override
    public void addRadioEventListener(RadioRoomListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            Logger.d("==============addRadioEventListener:messages-" + messages.size() + " listener size:" + listeners.size());
            if (!messages.isEmpty()) {
                listener.onLoadMessageHistory(messages);
            }
        }
    }

    public void setMiniRoomListener(OnMiniRoomListener onMiniRoomListener) {
        this.onMiniRoomListener = onMiniRoomListener;
    }

    @Override
    public void removeRadioEventListener(RadioRoomListener listener) {
        listeners.remove(listener);
        Logger.d("==============RadioEventHelper:removeRadioEventListener");
    }

    @Override
    public boolean isInRoom() {
        return !TextUtils.isEmpty(roomId);
    }

    @Override
    public void sendMessage(MessageContent messageContent) {
        if (TextUtils.isEmpty(roomId)) {
            Logger.e("roomId is empty, please register");
            return;
        }
        // 本地消息不发送出去
        if (messageContent instanceof RCChatroomLocationMessage) {
            Message message = new Message();
            message.setConversationType(Conversation.ConversationType.CHATROOM);
            message.setContent(messageContent);
            onMessageReceived(message);
            return;
        }
        RCMessager.getInstance().sendChatRoomMessage(roomId, messageContent, new SendMessageCallback() {
            @Override
            public void onAttached(Message message) {
            }

            @Override
            public void onSuccess(Message message) {
                onMessageReceived(message);
                Logger.d("=============sendChatRoomMessage:success");
            }

            @Override
            public void onError(Message message, int code, String reason) {
                if (messageContent instanceof RCChatroomBarrage || messageContent instanceof RCChatroomVoice) {
                    ToastUtils.s(UIKit.getContext(), "发送失败");
                }
                Logger.e("=============" + code + ":" + reason);
            }
        });
    }

    @Override
    public void onRadioRoomKVUpdate(IRCRadioRoomEngine.UpdateKey updateKey, String s) {
        switch (updateKey) {
            case RC_NOTICE:
                if (isSendDefaultMessage) {
                    sendNoticeModifyMessage();
                }
                break;
            case RC_SUSPEND:
                setSuspend(TextUtils.equals(s, "1"));
                break;
            case RC_SEATING:
                setInSeat(TextUtils.equals(s, "1"));
                break;
            case RC_SILENT:
                setMute(TextUtils.equals(s, "1"));
                break;
            case RC_SPEAKING:
                if (onMiniRoomListener != null) {
                    onMiniRoomListener.onSpeak(TextUtils.equals(s, "1"));
                }
                break;
        }
        for (RCRadioEventListener l : listeners) {
            l.onRadioRoomKVUpdate(updateKey, s);
        }
    }

    public boolean isShowingMessage(Message message) {
        MessageContent content = message.getContent();
        if (content instanceof RCChatroomBarrage || content instanceof RCChatroomEnter
                || content instanceof RCChatroomKickOut || content instanceof RCChatroomGiftAll
                || content instanceof RCChatroomGift || content instanceof RCChatroomAdmin
                || content instanceof RCChatroomLocationMessage || content instanceof RCFollowMsg
                || content instanceof RCChatroomVoice || content instanceof TextMessage) {
            return true;
        }
        return false;
    }

    /**
     * 发送公告更新的
     */
    private void sendNoticeModifyMessage() {
        RCChatroomLocationMessage tips = new RCChatroomLocationMessage();
        tips.setContent("房间公告已更新！");
        sendMessage(tips);
    }

    @Override
    public void onCloseMiniRoom(CloseResult closeResult) {
        onMiniRoomListener = null;
        // need leave room
        leaveRoom(new LeaveRoomCallback() {
            @Override
            public void leaveFinish() {
                changeUserRoom("");
                MusicManager.get().stopPlayMusic();
                unRegister();
                if (closeResult != null) {
                    closeResult.onClose();
                }
            }
        });
    }

    /**
     * 更改所属房间
     */
    private void changeUserRoom(String roomId) {
        HashMap<String, Object> params = new OkParams()
                .add("roomId", roomId)
                .build();
        OkApi.get(VRApi.USER_ROOM_CHANGE, params, new WrapperCallBack() {
            @Override
            public void onResult(Wrapper result) {
                if (result.ok()) {
                    Log.e("TAG", "changeUserRoom: " + result.getBody());
                }
            }
        });
    }

    /**
     * 上下切换房间的操作
     */
    public void switchRoom() {
        // 发送离开的消息
        RCChatroomLeave leave = new RCChatroomLeave();
        leave.setUserId(AccountStore.INSTANCE.getUserId());
        leave.setUserName(AccountStore.INSTANCE.getUserName());
        sendMessage(leave);
        // 移除监听
        removeListener();
    }

    /**
     * 离开房间
     *
     * @param leaveRoomCallback
     */
    public void leaveRoom(LeaveRoomCallback leaveRoomCallback) {
        // 调用切换房间以发送消息和移除监听
        switchRoom();
        // 离开房间的操作
        RCRadioRoomEngine.getInstance().leaveRoom(new RCRadioRoomCallback() {
            @Override
            public void onSuccess() {
                changeUserRoom("");
                Logger.d("==============leaveRoom onSuccess");
                if (leaveRoomCallback != null) {
                    leaveRoomCallback.leaveFinish();
                }
            }

            @Override
            public void onError(int code, String message) {
                Logger.e("==============leaveRoom onError");
                changeUserRoom("");
                if (leaveRoomCallback != null) {
                    leaveRoomCallback.leaveFinish();
                }
            }
        });
    }

    /**
     * 关闭房间
     *
     * @param closeRoomCallback
     */
    public void closeRoom(String roomId, CloseRoomCallback closeRoomCallback) {
        // 发送关闭房间的消息
        sendMessage(new RCRRCloseMessage());
        // 离开房间
        leaveRoom(() -> {
            // 删除房间
            deleteRoom(roomId, closeRoomCallback);
        });
    }

    /**
     * 房主关闭房间
     */
    private void deleteRoom(String roomId, CloseRoomCallback closeRoomCallback) {
        // 房主关闭房间，调用删除房间接口
        OkApi.get(VRApi.deleteRoom(roomId), null, new WrapperCallBack() {
            @Override
            public void onResult(Wrapper result) {
                if (closeRoomCallback != null) {
                    closeRoomCallback.onSuccess();
                }
            }

            @Override
            public void onError(int code, String msg) {
                super.onError(code, msg);
                if (closeRoomCallback != null) {
                    closeRoomCallback.onSuccess();
                }
            }
        });
    }

    private void removeListener() {
        MusicManager.get().stopPlayMusic();
        unRegister();
    }

    public interface CloseRoomCallback {
        void onSuccess();
    }

    public interface LeaveRoomCallback {
        void leaveFinish();
    }

    private static class Holder {
        static final RadioEventHelper INSTANCE = new RadioEventHelper();
    }
}
