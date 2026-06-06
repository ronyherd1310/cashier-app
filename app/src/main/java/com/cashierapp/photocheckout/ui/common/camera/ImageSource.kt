package com.cashierapp.photocheckout.ui.common.camera

public interface ImageSource {
    public suspend fun capture(): ByteArray
}
