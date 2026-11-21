# Photos Unlimited

[English](README.md) · [Português (Brasil)](README.pt-BR.md) · [Español](README.es.md) ·
**简体中文** · [日本語](README.ja.md)

*本译文由语言模型翻译，可能落后于英文 README；英文 README 为准。*

一个小巧、独立的 Android 上传工具，用于 Google 相册图库。选择文件夹，备份原始照片和视频，
并继续使用官方 Google 相册应用来浏览和编辑你的图库。

**Android 13+ · MIT 许可证**

[网站](https://venzellucas.github.io/photos-unlimited/) ·
[下载 APK](https://github.com/venzellucas/photos-unlimited/releases/latest/download/Photos-Unlimited.apk) ·
[发布说明](https://github.com/venzellucas/photos-unlimited/releases/latest)

**验证 APK。** 每个版本都使用证书签名，其 SHA-256 指纹为
`cbd14272c6aaa0568df8cc8be4ec10be9c6a2ebc0354eb202b5a05d8cda74bce`。Android 会拒绝任何
未使用此密钥签名的更新。每个版本还会在 `SHA256SUMS.txt` 中列出 APK 的校验和。
如需报告安全问题，请参阅 [SECURITY.md](SECURITY.md)。

## 功能

- 选择照片和视频文件夹，备份原始文件。
- 手动运行备份，或让 Android 调度自动任务，并可设置仅在不限流量网络和充电时运行。
- 查看当前文件的进度、上传历史、已存在的匹配项，以及需要重试的文件。
- 使用原生 Material 界面、浅色/深色主题、壁纸取色、自适应图标，以及（可用时）缓存的
  Google 账号头像。
- 扫描 Google 相册元数据，查看所报告的存储空间用量，并可按媒体类型以及计入或未知存储空间
  进行筛选。此功能为实验性预览。
- 应用支持 English、Português (Brasil)、Español、简体中文和日本語。它会跟随手机的语言；
  如需选择其他语言，请前往 设置 → **语言**。

无需 root、自定义 ROM、microG、API 密钥或 Photos Unlimited 服务器。

## 安装与连接

1. 从发布页面下载已签名的 APK。Android 可能会要求你允许从浏览器安装。覆盖安装现有版本
   可保留你的设置。
2. 通过应用中 Google 的设置页面连接你的账号。如果 Google 拒绝内嵌登录，请使用连接界面中
   说明的浏览器设置令牌备用方案。该令牌只能在应用中输入；切勿在 issue 或消息中发布。
3. 允许访问所有照片和视频以及媒体位置信息。后者用于读取完整的原始文件字节，包括嵌入的
   位置信息。
4. 选择文件夹。这些文件夹中的现有文件会包含在首次备份中。
5. 在官方 Google 相册应用中关闭自动备份，然后点按 **立即备份**。这同时会在
   Photos Unlimited 中启用自动备份。

应用在打开期间每天检查一次 GitHub，并在有新版本发布时显示一张卡片。可在
设置 → **检查更新** 中关闭此功能。如果账号按钮显示为首字母，请打开它并选择
**刷新个人资料照片**。头像查询使用单独的只读令牌，每天最多一次；
查询失败不会使上传凭据失效。高级的手机账号选择器仅用于诊断，不能替代备份登录。

## MVP 边界

应用使用 Google 未公开的移动协议，改编自 xob0t 的 GPMC 和 gotohp。它通过 Pixel
上传配置文件请求原始画质的处理方式。
**上传被接受并不能证明免占配额。** 存储空间的计算由 Google 控制，Google 可能更改或拒绝
这些方法。在依赖本应用之前，请验证新的示例上传、可下载的原始文件以及实际的配额使用情况。
图库中已存在的匹配项会被跳过，并保留其原有的计费方式。

存储空间扫描涵盖 Google 相册，不包括云端硬盘或 Gmail。未知的计费情况不等于零。其总数是
所扫描记录中报告的计入字节数之和，可能与整个账号的用量不同。共享项目、备用版本、遗漏
以及并发变更都可能影响覆盖范围。实时计费情况尚未完全验证。

## 释放存储空间

已占用 Google 存储空间的项目可以替换为通过本应用上传的副本：在 **存储空间** 中点按某个
项目，或选择多个项目（**全选符合条件的项目** 可跨页生效）。Google 通过内容哈希识别媒体，并用
同一个键删除，因此必须先删除原件，才能存在相同的替代项。因此每个项目会：

1. 对照 Google 的记录进行检查（不能有其他项目与其字节相同）。
2. 获取副本：手机上有则取自手机，否则从 Google 下载；仅当其 SHA-1 与 Google 为原始上传
   所记录的哈希相等时才接受。这证明文件字节完全一致，因此嵌入的元数据（EXIF 日期、GPS、
   相机、视频位置）会原样保留。
3. 移入回收站并永久删除，然后重新上传副本。
4. 仅当 Google 报告替代项的计入字节数为 0 时才确认；说明文字、收藏和归档状态会被恢复。

安全特性：

- 每一步在运行前都会写入日志；崩溃后可安全恢复。如果副本在删除前发生变化，则从回收站
  恢复原件。出现计入存储空间的替代项或任何需要处理的项目时，整个运行都会停止。
- 副本存放在 `Pictures/Photos Unlimited/Pending upload/` 中，而不是应用的私有存储空间，
  因此卸载或清除应用数据不会丢失它。文件名包含 SHA-1 和一个简短的账号哈希；新安装的应用
  会通过哈希验证每个遗留文件并完成处理（绝不会进入其他账号）。只有本应用写入的文件会被
  自动完成；早期安装留下的文件会先列出，等待你批准，而其他应用写入的文件会被忽略。只有当
  Google 显示存在一个有效、未被移入回收站且校验和相同的项目时，暂存的副本才会被删除。
  只要有任何内容仅存在于手机上，通知就会提醒你不要卸载，账号更改会等到处理完成后再进行，
  Android 的卸载对话框也会提供保留数据的选项。
- 在删除之前，会再次查询该项目，它必须仍然符合条件，移入回收站的操作必须在 Google 的
  记录中可见，并且在删除与确认替代项之间，同一时间只有一个项目。
- **请先关闭 Google 相册备份。** 开启备份时，Google 相册会在该账号的所有设备上，删除
  手机中已从云端移除的内容的副本
  （[Google 帮助](https://support.google.com/photos/answer/6128858)）。释放空间功能会
  要求你这样做，并作为安全保障，把被删除的手机副本按字节完全一致的方式放回去。
- **节省空间** 项目只有在原始文件仍在手机上时才会替换为原件（由 Google 记录的哈希证明）；
  绝不会改用 Google 的缩减副本。这也会升级 2021 年以前免费的缩减上传项目。
- **动态照片**（Pixel、Samsung）是单个文件，会保留其动态效果；Google 会重新提取相同的
  内嵌视频。**iPhone 的 Live Photos** 将视频保存在单独的文件中，因此会被拒绝处理，以免
  丢失动态效果。

替换后会丢失：相册、共享相册中的副本、评论、人脸分组，以及在 Google 相册内对日期/位置
或其他内容所做的编辑。其他开启了 Google 相册备份的设备仍可能删除它们的副本。已在测试
账号上进行实际测试（照片、视频、节省空间项目、一张动态照片，以及在清除应用数据后恢复的
一个运行到一半被中止的视频）；尚未在大型真实图库上测试。

### Google 相册备份提醒

同一部手机上的两个备份应用会相互竞争，而且 Google 相册的副本会占用存储空间。Android
不允许应用读取 Google 相册的设置，因此在自动备份开启期间，应用每次打开时都会询问，直到你
确认 Google 相册备份已关闭，并直接链接到其备份页面；只有当它之后发现有新照片先被其他
应用上传时，才会再次询问。

后台调度由 Android 控制。网络、电池、充电和空闲规则可能会延迟任务。强制停止应用会使
自动任务停止，直到再次打开应用。文件传输中断后会从头重新传输该文件。特别大的视频可能
需要在稳定的网络连接下手动运行。RAW、HDR 和编辑内容需要针对具体格式进行验证；分开的
照片/视频文件（iPhone Live Photos）不会被组合成配对。

## 隐私

请参阅 [PRIVACY.md](PRIVACY.md)。凭据加密存储在应用的私有存储空间中；
照片直接发送到 Google。本项目没有自有的账号服务器。
切勿在错误报告中包含凭据、Cookie、设置令牌或私人照片。

## 构建

使用 JDK 21 和 Android SDK platform/build tools 37。配置 `ANDROID_HOME`，或使用未纳入
版本控制的 `local.properties` 文件。仓库包含 Gradle wrapper。

```sh
./gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

调试版 APK 位于 `app/build/outputs/apk/debug/`。调试版无法更新已安装的签名发布版。
仅在专用的测试模拟器上运行 instrumentation 测试：

```sh
ANDROID_SERIAL=YOUR_TEST_EMULATOR ./gradlew :app:connectedDebugAndroidTest
```

构建发布版时，使用 `PHOTOS_KEYSTORE` 和 `PHOTOS_SIGN_PASSWORD` 提供外部密钥库，设置
`ANDROID_HOME`，然后运行 `RELEASE_TAG=vX.Y.Z tools/build-release.sh`，其中 `X.Y.Z` 是
`app/build.gradle` 中的 `versionName`。APK 及其 `SHA256SUMS.txt` 会写入 `dist/X.Y.Z/`。
切勿提交这些值或密钥库。更新时请保留同一个发布签名密钥。

图库掩码可以使用 `tools/generate-library-templates.py` 重新生成，需要 `blackboxprotobuf`
以及下文列出的固定版本 GPMC 检出。

## 验证

每个版本都会通过 JVM 和 Android instrumentation 测试套件、无错误的 lint，以及从上一版本
进行的原地签名升级。1.0.0 版本还经过了独立的安全审查（发布前已修复所有发现的问题），并
按上述方式进行了实际的释放空间运行。测试涵盖协议处理、已确认的上传、中断、持久化队列、
账号隔离、元数据分页、外观以及头像缓存隔离。测试凭据和网络响应均为合成数据。
已检查浅色/深色及大字体界面。这些检查不能证明实际免占配额，也不能证明图库计费情况完整。

## 翻译

应用、本 README 和网站提供英语、巴西葡萄牙语、西班牙语、简体中文和日语版本。译文由语言
模型翻译，可能落后于英文文本，英文文本为准。欢迎通过 pull request 提交更正：应用文本位于
`app/src/main/res/values-*/strings*.xml`，翻译后的 README 和网站页面与英文版放在一起。

## 致谢与许可证

采用 MIT 许可的协议改编：

- [xob0t/gpmc](https://github.com/xob0t/gpmc)，修订版本
  `94b1b267ca6819da542d4330a7dd216747eb0437`。
- [xob0t/gotohp](https://github.com/xob0t/gotohp)，修订版本
  `2a5c1b9678281b6a2ff08661a53ea1dc6b1e2edc`。

上游声明保留在
[app/src/main/assets/XOB0T-LICENSE.txt](app/src/main/assets/XOB0T-LICENSE.txt) 以及应用的
“关于”界面中。Android 和所捆绑的库保留各自的许可证。
请参阅 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

Google、Google 相册、Android 和 Pixel 是其各自所有者的商标。
Photos Unlimited 是独立项目，与 Google 没有关联。
