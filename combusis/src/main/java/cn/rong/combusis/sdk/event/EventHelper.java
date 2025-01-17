package cn.rong.combusis.sdk.event;

import static cn.rong.combusis.sdk.Api.EVENT_AGREE_MANAGE_PICK;
import static cn.rong.combusis.sdk.Api.EVENT_REJECT_MANAGE_PICK;
import static cn.rong.combusis.sdk.Api.EVENT_ROOM_CLOSE;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.basis.UIStack;
import com.basis.net.oklib.OkApi;
import com.basis.net.oklib.OkParams;
import com.basis.net.oklib.WrapperCallBack;
import com.basis.net.oklib.wrapper.Wrapper;
import com.kit.UIKit;
import com.kit.cache.GsonUtil;
import com.kit.utils.Logger;
import com.kit.wapper.IResultBack;
import com.rongcloud.common.utils.AccountStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import cn.rong.combusis.api.VRApi;
import cn.rong.combusis.common.ui.dialog.ConfirmDialog;
import cn.rong.combusis.message.RCChatroomAdmin;
import cn.rong.combusis.message.RCChatroomBarrage;
import cn.rong.combusis.message.RCChatroomEnter;
import cn.rong.combusis.message.RCChatroomGift;
import cn.rong.combusis.message.RCChatroomGiftAll;
import cn.rong.combusis.message.RCChatroomKickOut;
import cn.rong.combusis.message.RCChatroomLocationMessage;
import cn.rong.combusis.message.RCChatroomSeats;
import cn.rong.combusis.message.RCChatroomVoice;
import cn.rong.combusis.message.RCFollowMsg;
import cn.rong.combusis.music.MusicManager;
import cn.rong.combusis.provider.user.UserProvider;
import cn.rong.combusis.sdk.Api;
import cn.rong.combusis.sdk.VoiceRoomApi;
import cn.rong.combusis.sdk.event.listener.LeaveRoomCallBack;
import cn.rong.combusis.sdk.event.listener.RoomListener;
import cn.rong.combusis.sdk.event.listener.StatusListener;
import cn.rong.combusis.sdk.event.wrapper.EToast;
import cn.rong.combusis.sdk.event.wrapper.IEventHelp;
import cn.rong.combusis.sdk.event.wrapper.TipType;
import cn.rong.combusis.widget.miniroom.MiniRoomManager;
import cn.rongcloud.voiceroom.api.RCVoiceRoomEngine;
import cn.rongcloud.voiceroom.api.callback.RCVoiceRoomCallback;
import cn.rongcloud.voiceroom.api.callback.RCVoiceRoomEventListener;
import cn.rongcloud.voiceroom.api.callback.RCVoiceRoomResultCallback;
import cn.rongcloud.voiceroom.model.RCPKInfo;
import cn.rongcloud.voiceroom.model.RCVoiceRoomInfo;
import cn.rongcloud.voiceroom.model.RCVoiceSeatInfo;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.ChatRoomInfo;
import io.rong.imlib.model.ChatRoomMemberInfo;
import io.rong.imlib.model.Conversation;
import io.rong.imlib.model.Message;
import io.rong.imlib.model.MessageContent;
import io.rong.imlib.model.UserInfo;
import io.rong.message.TextMessage;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

public class EventHelper implements IEventHelp, RCVoiceRoomEventListener {

    private final static IEventHelp _helper = new EventHelper();
    public static final int STATUS_ON_SEAT = 0;
    public static final int STATUS_NOT_ON_SEAT = 1;
    public static final int STATUS_WAIT_FOR_SEAT = 2;
    protected final static Object obj = new Object();
    protected final String TAG = "1111";
    protected List<RoomListener> listeners;//房间监听
    protected List<StatusListener> statusListeners;//网络状态监听
    protected List<RCVoiceSeatInfo> mSeatInfos;//当前麦序
    protected RCVoiceRoomInfo roomInfo;//房间信息
    protected RCVoiceRoomEventListener rcVoiceRoomEventListener;
    protected List<MessageContent> messageList = new LinkedList<>();
    protected boolean isMute = false;
    protected int currentStatus;
    protected String roomId;
    private ConfirmDialog inviteDialog;

    protected void init(String roomId) {
        this.roomId = roomId;
        RCVoiceRoomEngine.getInstance().setVoiceRoomEventListener(this);
        if (null == mSeatInfos) mSeatInfos = new ArrayList<>();
        if (null == listeners) listeners = new ArrayList<>();
        if (null == statusListeners) statusListeners = new ArrayList<>();
    }

    protected void unInit() {
        RCVoiceRoomEngine.getInstance().setVoiceRoomEventListener(null);
        if (null != mSeatInfos) mSeatInfos.clear();
        if (null != listeners) listeners.clear();
        if (null != statusListeners) statusListeners.clear();
        messageList.clear();
        isMute = false;
        roomInfo = null;
        roomId = null;
    }


    @Override
    public void onRoomKVReady() {
        Log.d(TAG, "onRoomKVReady");
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onRoomKVReady();
        }
    }

    public RCVoiceRoomInfo getRoomInfo() {
        return roomInfo;
    }

    /**
     * 房间信息跟新回调
     *
     * @param room
     */
    @Override
    public void onRoomInfoUpdate(RCVoiceRoomInfo room) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onRoomInfoUpdate(room);
        }
        this.roomInfo = room;
        Log.d(TAG, "onRoomInfoUpdate:" + GsonUtil.obj2Json(roomInfo));
        if (null != listeners) {
            for (RoomListener l : listeners) {
                l.onRoomInfo(roomInfo);
            }
        }
    }

    /**
     * 麦位列表跟新回调
     *
     * @param list
     */
    @Override
    public void onSeatInfoUpdate(List<RCVoiceSeatInfo> list) {
        Log.d(TAG, "onSeatInfoUpdate: count = ");
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onSeatInfoUpdate(list);
        }
        int count = list.size();
        for (int i = 0; i < count; i++) {
            RCVoiceSeatInfo info = list.get(i);
            Log.d(TAG, "index = " + i + "  " + GsonUtil.obj2Json(info));
        }
        synchronized (obj) {
            mSeatInfos.clear();
            mSeatInfos.addAll(list);
        }
        refreshSeatInfos();
    }

    private void refreshSeatInfos() {
        if (null != listeners) {
            for (RoomListener l : listeners) {
                l.onSeatList(mSeatInfos);
            }
        }
    }

    //同步回调 onSeatInfoUpdate 此处无特殊需求可不处理
    @Override
    public void onUserEnterSeat(int index, String userId) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onUserEnterSeat(index, userId);
        }
        Log.d(TAG, "onUserEnterSeat: index = " + index + " userId = " + userId);
        RCVoiceSeatInfo info = getSeatInfo(index);
        if (null != info) {
            info.setUserId(userId);
            info.setStatus(RCVoiceSeatInfo.RCSeatStatus.RCSeatStatusUsing);
        }
        refreshSeatInfos();
    }

    //同步回调 onSeatInfoUpdate 此处无特殊需求可不处理
    @Override
    public void onUserLeaveSeat(int index, String userId) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onUserLeaveSeat(index, userId);
        }
        Log.d(TAG, "onUserLeaveSeat: index = " + index + " userId = " + userId);
        RCVoiceSeatInfo info = getSeatInfo(index);
        if (null != info) {
            info.setUserId(null);
            // 可能是锁定导致的
            // info.setStatus(RCVoiceSeatInfo.RCSeatStatus.RCSeatStatusEmpty);
        }
        refreshSeatInfos();
    }

    //同步回调 onSeatInfoUpdate 此处无特殊需求可不处理
    @Override
    public void onSeatMute(int index, boolean mute) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onSeatMute(index, mute);
        }
        Log.d(TAG, "onSeatMute: index = " + index + " mute = " + mute);
    }

    //同步回调 onSeatInfoUpdate 此处无特殊需求可不处理
    @Override
    public void onSeatLock(int index, boolean locked) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onSeatLock(index, locked);
        }
        Log.d(TAG, "onSeatLock: index = " + index + " locked = " + locked);
    }

    /**
     * 观众进入
     *
     * @param userId
     */
    @Override
    public void onAudienceEnter(String userId) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onAudienceEnter(userId);
        }
        Log.d(TAG, "onAudienceEnter: userId = " + userId);
        if (null != listeners) {
            getOnLineUserIds(roomId, new IResultBack<List<String>>() {
                @Override
                public void onResult(List<String> strings) {
                    for (RoomListener l : listeners) {
                        l.onOnLineUserIds(strings);
                    }
                }
            });
        }
    }

    /**
     * 观众离开房间
     *
     * @param userId
     */
    @Override
    public void onAudienceExit(String userId) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onAudienceExit(userId);
        }
        Log.d(TAG, "onAudienceExit: userId = " + userId);
        if (null != listeners) {
            getOnLineUserIds(roomId, new IResultBack<List<String>>() {
                @Override
                public void onResult(List<String> strings) {
                    for (RoomListener l : listeners) {
                        l.onOnLineUserIds(strings);
                    }
                }
            });
        }
    }

    /**
     * 说话状态回调 比较频繁
     *
     * @param index    麦位索引
     * @param speaking 是否正在语音
     */
    @Override
    public void onSpeakingStateChanged(int index, boolean speaking) {
        Log.v(TAG, "onSpeakingStateChanged: index = " + index + " speaking = " + speaking);
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onSpeakingStateChanged(index, speaking);
        }
        if (MiniRoomManager.getInstance().isShowing()) {
            MiniRoomManager.getInstance().onSpeak(speaking);
        }
//        Log.v(TAG, "onSpeakingStateChanged: index = " + index + " speaking = " + speaking);
        if (null != statusListeners) {
            for (StatusListener l : statusListeners) {
                l.onSpeaking(index, speaking);
            }
        }
    }

    @Override
    public void onMessageReceived(Message message) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onMessageReceived(message);
        }
        Class<? extends MessageContent> aClass = message.getContent().getClass();
        if ((RCChatroomVoice.class.equals(aClass) || RCChatroomLocationMessage.class.equals(aClass)
                || RCChatroomVoice.class.equals(aClass) || RCChatroomBarrage.class.equals(aClass)
                || RCChatroomEnter.class.equals(aClass) || RCChatroomKickOut.class.equals(aClass)
                || RCChatroomGift.class.equals(aClass) || RCChatroomAdmin.class.equals(aClass)
                || RCChatroomSeats.class.equals(aClass) || RCChatroomGiftAll.class.equals(aClass)
                || RCFollowMsg.class.equals(aClass) || TextMessage.class.equals(aClass))
                && message.getConversationType() == Conversation.ConversationType.CHATROOM) {
            addMessage(message.getContent());
        }
        if (message.getConversationType() == Conversation.ConversationType.PRIVATE) {
            if (null != statusListeners) {
                if (!TextUtils.isEmpty(roomId)) {
                    getUnReadMegCount(roomId, new IResultBack<Integer>() {
                        @Override
                        public void onResult(Integer integer) {
                            for (StatusListener l : statusListeners) {
                                l.onReceive(integer);
                            }
                        }
                    });
                }
            }
        }
        Log.v(TAG, "onMessageReceived: " + GsonUtil.obj2Json(message.getContent()));
    }

    /**
     * 房间通知回调
     *
     * @param name
     * @param content
     */
    @Override
    public void onRoomNotificationReceived(String name, String content) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onRoomNotificationReceived(name, content);
        }
        if (MiniRoomManager.getInstance().isShowing()) {
            switch (name) {
                case EVENT_ROOM_CLOSE://当前房间被关闭
                    ConfirmDialog confirmDialog = new ConfirmDialog(UIStack.getInstance().getTopActivity(), "当前直播已结束", true
                            , "确定", "", null, new Function0<Unit>() {
                        @Override
                        public Unit invoke() {
                            VoiceRoomApi.getApi().leaveRoom(null);
                            unregeister();
                            return null;
                        }
                    });
                    confirmDialog.setCancelable(false);
                    confirmDialog.show();
                    break;
                case EVENT_AGREE_MANAGE_PICK:
                    EToast.showToast("用户连线成功");
                    break;
                case EVENT_REJECT_MANAGE_PICK:
                    if (TextUtils.equals(content, AccountStore.INSTANCE.getUserId()))
                        EToast.showToast("用户拒绝邀请");
                    break;
            }
        }
        Log.v(TAG, "onRoomNotificationReceived: name = " + name + " content = " + content);
        if (null != listeners) {
            for (RoomListener l : listeners) {
                l.onNotify(name, content);
            }
        }
    }

    /**
     * 当前用户被抱上麦回调
     *
     * @param userId 发起邀请的用户id
     */
    @Override
    public void onPickSeatReceivedFrom(String userId) {
        Log.d(TAG, "onPickSeatReceivedFrom: userId = " + userId);
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onPickSeatReceivedFrom(userId);
        }
        if (MiniRoomManager.getInstance().isShowing()) {
            showPickReceivedDialog(true, userId);
        }
    }

    /**
     * 弹窗收到上麦邀请弹窗
     *
     * @param isCreate 是否是房主
     * @param userId   邀请人的ID
     */
    public void showPickReceivedDialog(boolean isCreate, String userId) {
        String pickName = isCreate ? "房主" : "管理员";
        inviteDialog = new ConfirmDialog(UIStack.getInstance().getTopActivity(),
                "您被" + pickName + "邀请上麦，是否同意?", true,
                "同意", "拒绝", new Function0<Unit>() {
            @Override
            public Unit invoke() {
                //拒绝
                inviteDialog.dismiss();
                VoiceRoomApi.getApi().notifyRoom(EVENT_REJECT_MANAGE_PICK, userId);
                return null;
            }
        }, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                //同意
                VoiceRoomApi.getApi().notifyRoom(Api.EVENT_AGREE_MANAGE_PICK, AccountStore.INSTANCE.getUserId());
                //获取可用麦位索引
                int availableIndex = getAvailableSeatIndex();
                if (availableIndex > -1) {
                    VoiceRoomApi.getApi().enterSeat(availableIndex, null);
                } else {
                    EToast.showToast("当前没有空余的麦位");
                }
                inviteDialog.dismiss();
                if (currentStatus == STATUS_WAIT_FOR_SEAT) {
                    //被邀请上麦了，并且同意了，如果该用户已经申请了上麦，那么主动撤销掉申请
                    VoiceRoomApi.getApi().cancelRequestSeat(new IResultBack<Boolean>() {
                        @Override
                        public void onResult(Boolean aBoolean) {
                            currentStatus = STATUS_ON_SEAT;
                        }
                    });
                }
                return null;
            }
        }
        );
        inviteDialog.show();
    }

    /**
     * 被踢下麦回调
     *
     * @param index 麦位索引
     */
    @Override
    public void onKickSeatReceived(int index) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onKickSeatReceived(index);
        }
        if (MiniRoomManager.getInstance().isShowing()) {
            EToast.showToast("您已被抱下麦位");
        }
        Log.d(TAG, "onPickSeatReceivedFrom: index = " + index);
    }

    /**
     * 房主或管理员同意当前用户的排麦申请的回调
     * 1. enterSeat
     */
    @Override
    public void onRequestSeatAccepted() {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onRequestSeatAccepted();
        }
        if (MiniRoomManager.getInstance().isShowing()) {
            currentStatus = STATUS_ON_SEAT;
            //加入麦位
            int availableIndex = getAvailableSeatIndex();
            if (availableIndex > -1) {
                VoiceRoomApi.getApi().enterSeat(availableIndex, null);
            } else {
                EToast.showToast("当前没有空余的麦位");
            }
        }
        Log.d(TAG, "onRequestSeatAccepted: ");
    }

    /**
     * 发送的排麦请求被房主或管理员拒绝
     */
    @Override
    public void onRequestSeatRejected() {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onRequestSeatRejected();
        }
        if (MiniRoomManager.getInstance().isShowing()) {
            currentStatus = STATUS_NOT_ON_SEAT;
        }
        Log.d(TAG, "onRequestSeatRejected: ");
        EToast.showToast("您的上麦申请被拒绝啦");
    }

    /**
     * 排麦列表发生变化
     * 1、获取申请排麦id列表
     * 2、过滤已经在房间的用户
     * 3、弹框提 同意、拒绝
     */
    @Override
    public void onRequestSeatListChanged() {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onRequestSeatListChanged();
        }
        Log.d(TAG, "onRequestSeatListChanged: ");
//        RCVoiceRoomEngine.getInstance().getRequestSeatUserIds(new RCVoiceRoomResultCallback<List<String>>() {
//            @Override
//            public void onSuccess(List<String> strings) {
//                Log.e(TAG, "getRequestSeatUserIds: ids = " + GsonUtil.obj2Json(strings));
//                List<String> requestIds = new ArrayList<>();
//                for (String id : strings) {
//                    if (null == getSeatInfo(id)) {//过滤 不再麦位上
//                        requestIds.add(id);
//                    }
//                }
//                if (!requestIds.isEmpty()) {
//                    String userId = requestIds.get(0);
//                    onShowTipDialog("", userId, TipType.RequestSeat, new IResultBack<Boolean>() {//申请上麦
//                        @Override
//                        public void onResult(Boolean result) {
//                            if (result) {
//                                //同意
//                                int index = getAvailableSeatIndex();
//                                if (index > -1) {
//                                    RCVoiceRoomEngine.getInstance().acceptRequestSeat(userId, null);
//                                } else {
//                                    EToast.showToast("当前没有空余的麦位");
//                                }
//                            } else {//拒绝
//                                RCVoiceRoomEngine.getInstance().rejectRequestSeat(userId, null);
//                            }
//                        }
//                    });
//                } else {//申请被取消
//                    EventDialogHelper.helper().dismissDialog();
//                }
//            }
//
//            @Override
//            public void onError(int i, String s) {
//                Log.e(TAG, "onError: code:" + i + " ,message = " + s);
//            }
//        });

    }

    /**
     * 收到邀请
     *
     * @param invitationId 邀请标识 Id
     * @param userId       发送邀请用户的标识
     * @param content      邀请内容 （用户可以自定义）
     */
    @Override
    public void onInvitationReceived(String invitationId, String userId, String content) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onInvitationReceived(invitationId, userId, content);
        }
        Log.d(TAG, "onInvitationReceived: invitationId = " + invitationId + " userId = " + userId + " content = " + content);
    }

    /**
     * 邀请被接受回调
     *
     * @param invitationId 邀请标识 Id
     */
    @Override
    public void onInvitationAccepted(String invitationId) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onInvitationAccepted(invitationId);
        }
        EToast.showToast("用户连线成功");
        Log.d(TAG, "onInvitationAccepted: invitationId = " + invitationId);
    }

    /**
     * 邀请被拒绝回调
     *
     * @param invitationId 邀请标识 Id
     */
    @Override
    public void onInvitationRejected(String invitationId) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onInvitationRejected(invitationId);
        }
        EToast.showToast("用户拒绝邀请");
        Log.d(TAG, "onInvitationRejected: invitationId = " + invitationId);
    }

    /**
     * 邀请被取消回调
     *
     * @param invitationId 邀请标识 Id
     */
    @Override
    public void onInvitationCancelled(String invitationId) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onInvitationCancelled(invitationId);
        }
        Log.d(TAG, "onInvitationCancelled: invitationId = " + invitationId);
    }

    /**
     * 被踢出房间回调
     *
     * @param targetId 被踢用户的标识
     * @param userId   发起踢人用户的标识
     */
    @Override
    public void onUserReceiveKickOutRoom(String targetId, String userId) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onUserReceiveKickOutRoom(targetId, userId);
        }
        if (MiniRoomManager.getInstance().isShowing()) {
            if (targetId.equals(AccountStore.INSTANCE.getUserId())) {
                EToast.showToast("你已被踢出房间");
                MiniRoomManager.getInstance().close();
                VoiceRoomApi.getApi().leaveRoom(null);
                unregeister();
            }
        }
        Log.d(TAG, "onUserReceiveKickOutRoom: targetId = " + targetId);
    }

    /**
     * 网络状态
     *
     * @param i 网络延迟 ms
     */
    @Override
    public void onNetworkStatus(int i) {
        if (rcVoiceRoomEventListener != null) {
            rcVoiceRoomEventListener.onNetworkStatus(i);
        }
//        Log.d(TAG, "onNetworkStatus: rtt = " + i);
        if (null != statusListeners) {
            for (StatusListener l : statusListeners) {
                l.onStatus(i);
            }
        }
    }

    private EventHelper() {
    }

    public static IEventHelp helper() {
        return _helper;
    }

    @Override
    public boolean isInitlaized() {
        return !TextUtils.isEmpty(roomId);
    }


    public void regeister(String roomId) {
        init(roomId);
    }

    @Override
    public String getRoomId() {
        if (TextUtils.isEmpty(roomId)) {
            return "";
        }
        return roomId;
    }

    @Override
    public void addMessage(MessageContent message) {
        messageList.add(message);
    }

    @Override
    public List<MessageContent> getMessageList() {
        return messageList;
    }

    @Override
    public List<RCVoiceSeatInfo> getRCVoiceSeatInfoList() {
        return mSeatInfos;
    }

    @Override
    public boolean getMuteAllRemoteStreams() {
        return isMute;
    }

    @Override
    public void setMuteAllRemoteStreams(boolean isMute) {
        this.isMute = isMute;
    }

    @Override
    public void unregeister() {
        unInit();
    }

    @Override
    public void addRoomListener(RoomListener listener) {
        if (null == listeners) listeners = new ArrayList<>();
        listeners.add(listener);
    }

    @Override
    public void setRCVoiceRoomEventListener(RCVoiceRoomEventListener rcVoiceRoomEventListener) {
        this.rcVoiceRoomEventListener = rcVoiceRoomEventListener;
    }

    @Override
    public void removeRCVoiceRoomEventListener() {
        this.rcVoiceRoomEventListener = null;
    }

    /**
     * 离开当前房间
     *
     * @param callback
     */
    @Override
    public void leaveRoom(LeaveRoomCallBack callback) {
        RCVoiceRoomEngine.getInstance().leaveRoom(new RCVoiceRoomCallback() {
            @Override
            public void onSuccess() {
                MusicManager.get().stopPlayMusic();
                unregeister();
                changeUserRoom("");
                if (callback != null)
                    callback.onSuccess();
            }

            @Override
            public void onError(int i, String s) {
                if (callback != null)
                    callback.onError(i, s);
            }
        });
    }

    //更改所属房间
    @Override
    public void changeUserRoom(String roomId) {
        HashMap<String, Object> params = new OkParams()
                .add("roomId", roomId)
                .build();
        OkApi.get(VRApi.USER_ROOM_CHANGE, params, new WrapperCallBack() {
            @Override
            public void onResult(Wrapper result) {
                if (result.ok()) {
                    Log.e(TAG, "onResult: " + result.getMessage());
                }
            }
        });
    }

    @Override
    public int getCurrentStatus() {
        return currentStatus;
    }

    @Override
    public void setCurrentStatus(int status) {
        currentStatus = status;
    }

    @Override
    public void addStatusListener(StatusListener listener) {
        if (null == statusListeners) statusListeners = new ArrayList<>();
        statusListeners.add(listener);
    }

    /**
     * 根据用户id获取麦位信息
     *
     * @param userId
     * @return 麦位信息
     */
    public RCVoiceSeatInfo getSeatInfo(String userId) {
        synchronized (obj) {
            if (mSeatInfos == null) return null;
            int count = mSeatInfos.size();
            for (int i = 0; i < count; i++) {
                RCVoiceSeatInfo s = mSeatInfos.get(i);
                if (userId.equals(s.getUserId())) {
                    return s;
                }
            }
            return null;
        }
    }

    /**
     * @param index 索引
     * @return 麦位信息
     */
    public RCVoiceSeatInfo getSeatInfo(int index) {
        int count = null != mSeatInfos ? mSeatInfos.size() : 0;
        if (index < count) {
            synchronized (obj) {
                mSeatInfos.get(index);
            }
        }
        return null;
    }

    @Override
    public void getOnLineUserIds(String roomId, IResultBack<List<String>> resultBack) {
        RongIMClient.getInstance()
                .getChatRoomInfo(roomId,
                        20,
                        ChatRoomInfo.ChatRoomMemberOrder.RC_CHAT_ROOM_MEMBER_ASC,
                        new RongIMClient.ResultCallback<ChatRoomInfo>() {
                            @Override
                            public void onSuccess(ChatRoomInfo chatRoomInfo) {
                                Logger.d("=======" + chatRoomInfo.getTotalMemberCount());
                                if (null != resultBack && null != chatRoomInfo) {
                                    List<ChatRoomMemberInfo> cs = chatRoomInfo.getMemberInfo();
                                    int count = null == cs ? 0 : cs.size();
                                    List<String> result = new ArrayList<>();
                                    for (int i = 0; i < count; i++) {
                                        result.add(cs.get(i).getUserId());
                                    }
                                    resultBack.onResult(result);
                                }
                            }

                            @Override
                            public void onError(RongIMClient.ErrorCode err) {
                                Logger.e(TAG, "getOnLineUserIds#onError code = " + err.code + " msg = " + err.getMessage());
                                if (null != resultBack) resultBack.onResult(new ArrayList<>());
                            }
                        }
                );


    }

    @Override
    public void getUnReadMegCount(String roomId, IResultBack<Integer> resultBack) {
        RongIMClient.getInstance().getUnreadCount(new RongIMClient.ResultCallback<Integer>() {
            @Override
            public void onSuccess(Integer integer) {
                resultBack.onResult(integer);
            }

            @Override
            public void onError(RongIMClient.ErrorCode err) {
                Logger.e(TAG, "getUnReadMegCount#onError code = " + err.code + " msg = " + err.getMessage());
                if (null != resultBack) resultBack.onResult(0);
            }
        });
    }

    @Override
    public void getRequestSeatUserIds(IResultBack<List<String>> resultBack) {
        RCVoiceRoomEngine.getInstance().getRequestSeatUserIds(new RCVoiceRoomResultCallback<List<String>>() {
            @Override
            public void onSuccess(List<String> strings) {
                if (null != resultBack) {
                    List<String> requestIds = new ArrayList<>();
                    for (String id : strings) {
                        if (null == getSeatInfo(id)) {//筛选 不再麦位上
                            requestIds.add(id);
                        }
                    }
                    resultBack.onResult(requestIds);
                }
            }

            @Override
            public void onError(int i, String s) {
                Logger.e(TAG, "getRequestSeatUserIds#onError code = " + i + " msg = " + s);
            }
        });
    }

    /**
     * 获取可用麦位索引
     *
     * @return 可用麦位索引
     */
    public int getAvailableSeatIndex() {
        synchronized (obj) {
            int availableIndex = -1;
            for (int i = 0; i < mSeatInfos.size(); i++) {
                RCVoiceSeatInfo seat = mSeatInfos.get(i);
                if (RCVoiceSeatInfo.RCSeatStatus.RCSeatStatusEmpty == seat.getStatus()) {
                    availableIndex = i;
                    break;
                }
            }
            return availableIndex;
        }
    }

//    @Override
//    public PKInviter getPKInviter() {
//        return pkInviter;
//    }
//
//    @Override
//    public void releasePKInviter() {
//        pkInviter = null;
//    }

    protected void onShowTipDialog(String roomId, String userId, TipType type, IResultBack<Boolean> resultBack) {
        // 根据userId获取用户信息
        if (null == UIStack.getInstance().getTopActivity()) {
            Logger.e(TAG, "onShowTipDialog:  topActivity is null");
            return;
        }
        UserProvider.provider().getAsyn(userId, new IResultBack<UserInfo>() {
            @Override
            public void onResult(UserInfo userInfo) {
                Logger.e(TAG, "onShowTipDialog: " + GsonUtil.obj2Json(userInfo));
                if (null != userInfo) {
                    UIKit.runOnUiTherad(new Runnable() {// fix：子线程弹不出弹框
                        @Override
                        public void run() {
                            String message = "";
//                            if (TipType.InvitedSeat == type) {
//                                message = userInfo.getName() + "邀请您上麦，是否同意？";
//                                EventDialogHelper.helper().showTipDialog(UIStack.getInstance().getTopActivity(), type.getValue(), message, resultBack);
//                            } else if (TipType.RequestSeat == type) {
//                                message = userInfo.getName() + "申请上麦，是否同意？";
//                                EventDialogHelper.helper().showTipDialog(UIStack.getInstance().getTopActivity(), type.getValue(), message, resultBack);
//                            } else {
//                                EventDialogHelper.helper().showPKDialog(UIStack.getInstance().getTopActivity(), type.getValue(), roomId, userInfo, resultBack);
//                            }
                        }
                    });
                } else {
                    if (null != resultBack) resultBack.onResult(false);
                }
            }
        });

    }

    @Override
    public void onCloseMiniRoom(CloseResult closeResult) {
        leaveRoom(new LeaveRoomCallBack() {
            @Override
            public void onSuccess() {
                if (closeResult != null) {
                    closeResult.onClose();
                }
            }

            @Override
            public void onError(int code, String message) {

            }
        });
    }

    @Override
    public void onPKGoing(@NonNull RCPKInfo rcpkInfo) {

    }

    @Override
    public void onPKFinish() {

    }

    @Override
    public void onReveivePKInvitation(String s, String s1) {

    }

    @Override
    public void onPKInvitationCanceled(String s, String s1) {

    }

    @Override
    public void onPKInvitationRejected(String s, String s1) {

    }

    @Override
    public void onPKInvitationIgnored(String s, String s1) {

    }
}