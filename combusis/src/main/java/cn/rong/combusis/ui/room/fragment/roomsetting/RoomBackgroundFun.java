package cn.rong.combusis.ui.room.fragment.roomsetting;

import cn.rong.combusis.R;

/**
 * @author gyn
 * @date 2021/9/30
 */
public class RoomBackgroundFun extends IFun.BaseFun {
    public RoomBackgroundFun(int status) {
        setStatus(status);
    }

    @Override
    public int getIcon() {
        return R.drawable.ic_room_setting_bg;
    }

    @Override
    public String getText() {
        return "房间背景";
    }
}
