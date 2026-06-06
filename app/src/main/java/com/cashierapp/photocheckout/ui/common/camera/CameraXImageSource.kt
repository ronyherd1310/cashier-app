package com.cashierapp.photocheckout.ui.common.camera

public class CameraXImageSource : ImageSource {
    override suspend fun capture(): ByteArray = error("CameraX capture is wired through CameraCaptureScreen in the add-product flow.")
}
