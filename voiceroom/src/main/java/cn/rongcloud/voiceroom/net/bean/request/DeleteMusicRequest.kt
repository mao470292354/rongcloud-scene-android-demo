/*
 * Copyright © 2021 RongCloud. All rights reserved.
 */

package cn.rongcloud.voiceroom.net.bean.request

import com.google.gson.annotations.SerializedName


/**
 * @author gusd
 * @Date 2021/06/17
 */
data class DeleteMusicRequest(
    @SerializedName("id")
    val id: Int = 0,
    @SerializedName("roomId")
    val roomId: String? = null
)