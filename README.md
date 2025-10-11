# TileDownloader

批量下载地图瓦片的小工具，支持在设定的经纬度范围内、多个缩放级别并发抓取在线瓦片服务数据。适合需要离线缓存 OpenStreetMap CyclOSM 或 ArcGIS 等在线底图的场景。

## 功能概览
- 按照经纬度范围自动换算瓦片 `x/y` 区间并循环下载
- 支持 `ZXY`（OpenStreetMap）与 `ZYX`（ArcGIS）两种 URL 模式
- 跳过已存在的瓦片文件，避免重复写入
- 线程池并发抓取，定期输出进度与最后一个文件路径
- 支持自定义缓冲区大小、线程数、进度提示等参数

## 环境要求
- Java 20（或兼容的 JDK）
- Maven 3.9+（用于构建与运行）
- 稳定的网络连接（访问瓦片服务）

## 快速开始

1. **编译打包**
   ```powershell
   mvn clean package
   ```
   打包后会生成 `target/MapDownloader-1.0-SNAPSHOT.jar`。

2. **运行示例**
   ```powershell
   java -cp target/MapDownloader-1.0-SNAPSHOT.jar com.yuqiangdede.TileDownloader
   ```
   程序默认下载 CyclOSM 瓦片，并存储到 `D:\temp` 目录下。

> 注意：使用前请确认目标瓦片服务允许批量下载，并遵守服务方的使用政策与限速要求。

## 关键参数
源码中 `TileDownloader` 类的顶层常量控制下载行为，修改后需重新编译。常用参数如下表：

| 常量 | 说明 | 默认值 |
| --- | --- | --- |
| `BASE_DIRECTORY` | 本地保存路径，建议改为存在的磁盘目录 | `D:\temp` |
| `BASE_URL` | 瓦片服务根地址，已预置 CyclOSM 与 ArcGIS 示例 | `http://a.tile-cyclosm.openstreetmap.fr/cyclosm/` |
| `START_LON` / `END_LON` | 经度范围（西到东） | `117` / `118` |
| `START_LAT` / `END_LAT` | 纬度范围（南到北） | `38` / `39` |
| `MIN_ZOOM` / `MAX_ZOOM` | 缩放层级范围（含两端） | `0` / `12` |
| `THREADS` | 并发下载线程数 | `16` |
| `START` | 用于跳过前若干个瓦片（容错重跑） | `0` |
| `PROGRESS_STEP` / `LARGE_PROGRESS_STEP` | 进度日志频率 | `100` / `10_000` |
| `BUFFER_SIZE` | 下载写入缓冲区大小（字节） | `8192` |

## 工作流程简介
1. 根据设定的经纬度、缩放层级计算瓦片编号范围。
2. 按瓦片循环构建 URL，并提交到固定大小的线程池。
3. 下载前检测本地文件是否存在且非空，避免重复请求。
4. 请求瓦片时设定连接/读取超时；保存成功后按配置输出进度。

## 常见定制
- **切换底图**：将 `BASE_URL` 改为目标服务地址，并确认使用 `ZXY` 或 `ZYX` 模式。
- **限制下载范围**：收紧经纬度或缩放层级，减少请求数量。
- **控制并发**：调整 `THREADS`，在速度与服务端限流之间平衡。
- **断点续传**：运行中断后，将 `START` 设置为已成功瓦片数量，继续执行即可。

## 后续改进方向
- 通过配置文件或命令行参数接受运行时配置，避免重新编译。
- 增加失败重试、指数退避以及磁盘容量检测。
- 清理 `pom.xml` 中重复或未使用的依赖，减小体积。

如需反馈问题或贡献代码，欢迎提交 Issue 或 Pull Request。
