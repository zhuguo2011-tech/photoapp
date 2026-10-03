package com.nanjing.photoapp

import com.nanjing.photoapp.model.Photo

// 用来在相册页和大图查看页之间传递照片列表。
// 因为几百上千张照片的列表塞进Intent会超出系统约1MB的限制导致闪退，
// 所以改用这个内存对象中转（同一个进程内共享，跳转时不走序列化）。
object PhotoStore {
    var photos: List<Photo> = emptyList()

    // ===== 新版增加 =====
    // 大图页翻到已加载的最后几张时，可以接着从服务器加载下一批，所以需要知道：
    var albumId: Int = -1        // 是哪个相册
    var totalCount: Int = 0      // 这个相册一共多少张（用于显示“第几张 / 共几张”）
    var hasMore: Boolean = false // 服务器上是否还有没加载的
    var version: Int = 0         // 大图页每加载一批就+1，回到相册页时据此把新加载的也同步到格子列表里
    var lastViewedIndex: Int = -1 // 大图页最后看的是第几张，回到相册页时滚动到它附近
}
