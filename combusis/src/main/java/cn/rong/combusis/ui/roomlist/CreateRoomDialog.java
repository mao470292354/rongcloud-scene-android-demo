package cn.rong.combusis.ui.roomlist;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;

import com.basis.net.LoadTag;
import com.basis.net.oklib.OkApi;
import com.basis.net.oklib.WrapperCallBack;
import com.basis.net.oklib.api.body.FileBody;
import com.basis.net.oklib.wrapper.Wrapper;
import com.basis.widget.BottomDialog;
import com.kit.utils.ImageLoader;
import com.rongcloud.common.utils.ImageLoaderUtil;
import com.rongcloud.common.utils.LocalDataStore;
import com.rongcloud.common.utils.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import cn.rong.combusis.R;
import cn.rong.combusis.api.VRApi;
import cn.rong.combusis.common.ui.dialog.InputPasswordDialog;
import cn.rong.combusis.common.utils.ChineseLengthFilter;
import cn.rong.combusis.common.utils.RealPathFromUriUtils;
import cn.rong.combusis.provider.voiceroom.RoomType;
import cn.rong.combusis.provider.voiceroom.VoiceRoomBean;
import io.rong.imkit.picture.tools.ToastUtils;

/**
 * @author gyn
 * @date 2021/9/16
 */
public class CreateRoomDialog extends BottomDialog {
    private ImageView mCoverImage;
    private ActivityResultLauncher mLauncher;
    private String mCoverUrl;
    private String mRoomBackground;
    private RoomType mRoomType;
    private String mPassword = "";

    private EditText mRoomNameEditText;
    private RadioButton mPrivateButton;

    private CreateRoomCallBack mCreateRoomCallBack;
    private InputPasswordDialog mInputPasswordDialog;
    private LoadTag mLoading;

    public CreateRoomDialog(Activity activity, ActivityResultLauncher launcher, RoomType roomType, CreateRoomCallBack createRoomCallBack) {
        super(activity);
        this.mLauncher = launcher;
        this.mRoomType = roomType;
        this.mCreateRoomCallBack = createRoomCallBack;
        setContentView(R.layout.dialog_create_room, -1, UiUtils.INSTANCE.dp2Px(activity, 590));
        initView();
    }

    public void setCoverUri(Uri coverUri) {
        this.mCoverUrl = RealPathFromUriUtils.getRealPathFromUri(mActivity, coverUri);
        ImageLoader.loadUri(mCoverImage, coverUri, R.drawable.ic_create_voice_room_default_cover);
    }

    private void initView() {
        // 关闭
        getContentView().findViewById(R.id.iv_fold).setOnClickListener(v -> {
            dismiss();
        });
        // 房间封面
        mCoverImage = getContentView().findViewById(R.id.iv_room_cover);
        mCoverImage.setOnClickListener(v -> {
            startPicSelectActivity();
        });
        // 房间背景
        mRoomBackground = LocalDataStore.INSTANCE.getBackgroundByIndex(0);
        ImageView backgroundImage = getContentView().findViewById(R.id.iv_background);
        if (!TextUtils.isEmpty(mRoomBackground)) {
            ImageLoaderUtil.INSTANCE.loadImage(mActivity, backgroundImage, mRoomBackground, R.drawable.bg_create_room);
        }
        ImageView[] roomImages = new ImageView[]{getContentView().findViewById(R.id.iv_voice_room_bg_0),
                getContentView().findViewById(R.id.iv_voice_room_bg_1),
                getContentView().findViewById(R.id.iv_voice_room_bg_2),
                getContentView().findViewById(R.id.iv_voice_room_bg_3)};
        TextView[] gifTexts = new TextView[]{
                getContentView().findViewById(R.id.tv_is_gif_0),
                getContentView().findViewById(R.id.tv_is_gif_1),
                getContentView().findViewById(R.id.tv_is_gif_2),
                getContentView().findViewById(R.id.tv_is_gif_3)
        };
        RadioButton[] roomRadios = new RadioButton[]{
                getContentView().findViewById(R.id.rb_background_0),
                getContentView().findViewById(R.id.rb_background_1),
                getContentView().findViewById(R.id.rb_background_2),
                getContentView().findViewById(R.id.rb_background_3)
        };
        for (int i = 0; i < roomImages.length; i++) {
            final String imageUrl = LocalDataStore.INSTANCE.getBackgroundByIndex(i);
            if (!TextUtils.isEmpty(imageUrl)) {
                ImageLoaderUtil.INSTANCE.loadImage(mActivity, roomImages[i], imageUrl, R.color.transparent);
                if (imageUrl.toLowerCase(Locale.ROOT).endsWith("gif")) {
                    gifTexts[i].setVisibility(View.VISIBLE);
                }
            }
            int finalI = i;
            roomImages[i].setOnClickListener(v -> {
                for (int i1 = 0; i1 < roomRadios.length; i1++) {
                    roomRadios[i1].setChecked(finalI == i1);
                }
                ImageLoaderUtil.INSTANCE.loadImage(mActivity, backgroundImage, imageUrl, R.drawable.bg_create_room);
                mRoomBackground = imageUrl;
            });
        }
        // 创建房间
        getContentView().findViewById(R.id.btn_create_room).setOnClickListener(v -> {
            preCreateRoom();
        });

        mRoomNameEditText = getContentView().findViewById(R.id.et_room_name);
        mPrivateButton = getContentView().findViewById(R.id.rb_private);
        mRoomNameEditText.setFilters(new InputFilter[]{new ChineseLengthFilter(20)});
        mLoading = new LoadTag(mActivity, mActivity.getString(R.string.text_creating_room));
    }

    /**
     * 创建房间前逻辑判断
     */
    private void preCreateRoom() {
        // 房间名检测
        String roomName = mRoomNameEditText.getText() == null ? "" : mRoomNameEditText.getText().toString();
        if (TextUtils.isEmpty(roomName)) {
            ToastUtils.s(mActivity, mActivity.getString(R.string.please_input_room_name));
            return;
        }
        // 私密房密码检测
        if (mPrivateButton.isChecked() && TextUtils.isEmpty(mPassword)) {
            mInputPasswordDialog = new InputPasswordDialog(mActivity, false, () -> null, s -> {
                if (TextUtils.isEmpty(s)) {
                    return null;
                }
                if (s.length() < 4) {
                    ToastUtils.s(mActivity, mActivity.getString(R.string.text_please_input_four_number));
                    return null;
                }
                mPassword = s;
                mInputPasswordDialog.dismiss();
                uploadThemePic(roomName);
                return null;
            });
            mInputPasswordDialog.show();
        } else {
            uploadThemePic(roomName);
        }
    }

    private void uploadThemePic(String roomName) {
        // 选择本地图片后，先上传本地图片
        if (!TextUtils.isEmpty(mCoverUrl)) {
            mLoading.show();
            FileBody body = new FileBody("multipart/form-data", new File(mCoverUrl));
            OkApi.file(VRApi.FILE_UPLOAD, "file", body, new WrapperCallBack() {
                @Override
                public void onResult(Wrapper result) {
                    String url = result.getBody().getAsString();
                    if (result.ok() && !TextUtils.isEmpty(url)) {
                        createRoom(roomName, VRApi.FILE_PATH + url);
                    } else {
                        ToastUtils.s(mActivity, result.getMessage());
                        mLoading.dismiss();
                    }
                }

                @Override
                public void onError(int code, String msg) {
                    super.onError(code, msg);
                    ToastUtils.s(mActivity, msg);
                    mLoading.dismiss();
                }
            });
        } else {
            mLoading.show();
            createRoom(roomName, "");
        }
    }

    /**
     * 创建房间
     *
     * @param roomName
     * @param themeUrl
     */
    private void createRoom(String roomName, String themeUrl) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", roomName);
        params.put("themePictureUrl", themeUrl);
        params.put("isPrivate", mPrivateButton.isChecked() ? 1 : 0);
        params.put("password", mPassword);
        params.put("backgroundUrl", mRoomBackground);
        params.put("kv", new ArrayList());
        params.put("roomType", mRoomType.getType());
        OkApi.post(VRApi.ROOM_CREATE, params, new WrapperCallBack() {
            @Override
            public void onResult(Wrapper result) {
                if (mCreateRoomCallBack != null) {
                    VoiceRoomBean voiceRoomBean = result.get(VoiceRoomBean.class);
                    if (result.ok() && voiceRoomBean != null) {
                        dismiss();
                        mCreateRoomCallBack.onCreateSuccess(voiceRoomBean);
                    } else if (30016 == result.getCode() && voiceRoomBean != null) {
                        dismiss();
                        mCreateRoomCallBack.onCreateExist(voiceRoomBean);
                    } else {
                        ToastUtils.s(mActivity, result.getMessage());
                    }
                }
                mLoading.dismiss();
            }

            @Override
            public void onError(int code, String msg) {
                super.onError(code, msg);
                ToastUtils.s(mActivity, msg);
                mLoading.dismiss();
            }
        });
    }

    /**
     * 选择图片
     */
    private void startPicSelectActivity() {
        Intent intent = new Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        );
        if (mLauncher != null) {
            mLauncher.launch(intent);
        }
    }

    public interface CreateRoomCallBack {
        void onCreateSuccess(VoiceRoomBean voiceRoomBean);

        void onCreateExist(VoiceRoomBean voiceRoomBean);
    }
}
