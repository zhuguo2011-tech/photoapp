package com.nanjing.photoapp

import com.nanjing.photoapp.model.Photo

// 用来在相册页和大图查看页之间传递照片列表。
// 因为几百上千张照片的列表塞进Intent会超出系统约1MB的限制导致闪退，
// 所以改用这个内存对象中转（同一个进程内共享，跳转时不走序列化）。
object PhotoStore {
    var photos: List<Photo> = emptyList()
}
