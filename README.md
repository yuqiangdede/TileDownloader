# Tile Downloader（地图瓦片下载器）

Tile Downloader 是一个用于批量下载在线地图瓦片的 Java 工具。它支持多个地图服务商（OpenStreetMap、ArcGIS、OpenSeaMap、高德、谷歌），可以根据指定的经纬度范围和缩放等级构建离线缓存。

## 亮点
- 自动将地理范围和缩放等级转换为瓦片索引范围
- 同时兼容 ZXY（OpenStreetMap 风格）与 ZYX（ArcGIS 风格）URL 模板
- 自动跳过已存在的瓦片，避免重复下载
- 可配置线程池支持并发下载，并实时输出进度
- 同时提供命令行和 Swing 桌面 UI，GUI 支持多选复选框和手动输入边界框
- 可选 SOCKS 代理，并可灵活设置输出目录、缩放范围（默认 0-13）和线程数

## 环境要求
- Java 20（或兼容的 JDK）
- Maven 3.9+
- 能访问目标地图服务的网络环境

## 构建与运行
1. **构建**
   ```powershell
   mvn clean package
   ```
   会在 `target/MapDownloader-1.0-SNAPSHOT.jar` 中生成可执行文件。

2. **运行（命令行）**
   ```powershell
   java -cp target/MapDownloader-1.0-SNAPSHOT.jar com.yuqiangdede.TileDownloader
   ```
   所有已配置的地图源将下载至 `BASE_DIRECTORY` 指定的目录（默认 `D:\temp`）。

3. **运行（桌面 UI）**
   ```powershell
   java -cp target/MapDownloader-1.0-SNAPSHOT.jar com.yuqiangdede.TileDownloaderUI
   ```
   在图形界面中选择地图源、手动输入经纬度边界；右侧的选择列表会同步显示预览信息。

> 请始终遵守各地图服务的使用政策与访问频率限制。

## 支持的地图源
以下 ID 可用于命令行参数 `--sources`，也能在 UI 中勾选：

### OpenStreetMap 系列（ZXY）
| ID | 名称 |
| --- | --- |
| `osm-standard` | OpenStreetMap 标准地图 |
| `osm-hot` | 人道主义图层 |
| `cyclosm` | CyclOSM 等高线地图 |

### ArcGIS 系列（ZYX）
| ID | 名称 |
| --- | --- |
| `arcgis-topo` | ArcGIS 地形图 |
| `arcgis-imagery` | ArcGIS 影像图 |

### OpenSeaMap（ZXY）
| ID | 名称 |
| --- | --- |
| `seamap-base` | 海图基础图层 |
| `seamap-seamark` | 海标叠加图层 |

### 高德地图（ZXY）
| ID | 名称 |
| --- | --- |
| `gaode-satellite` | 高德卫星图 |
| `gaode-hybrid` | 高德卫星混合图 |
| `gaode-roadnet` | 高德道路网络图 |
| `gaode-light` | 高德浅色（密集标签） |
| `gaode-light-poi` | 高德浅色（稀疏标签） |

### 谷歌地图（ZXY）
| ID | 名称 |
| --- | --- |
| `google-vector` | 谷歌矢量图 |
| `google-satellite` | 谷歌卫星图 |
| `google-hybrid` | 谷歌混合图 |
| `google-terrain` | 谷歌地形图 |
| `google-terrain-labels` | 谷歌地形图（含标签） |
| `google-roads` | 谷歌道路叠加层 |

## 命令行参数（默认值）
| 常量 | 说明 | 默认值 |
| --- | --- | --- |
| `BASE_DIRECTORY` | 输出目录 | `D:\temp` |
| `START_LON` / `END_LON` | 经度范围 | `117` / `118` |
| `START_LAT` / `END_LAT` | 纬度范围 | `38` / `39` |
| `MIN_ZOOM` / `MAX_ZOOM` | 缩放级别 | `0` / `13` |
| `THREADS` | 下载线程数 | `16` |
| `PROGRESS_STEP` / `LARGE_PROGRESS_STEP` | 进度日志间隔 | `100` / `10_000` |
| `BUFFER_SIZE` | 下载缓冲区（字节） | `8192` |
| `DOWNLOAD_TYPE` | 命令行默认源选择 | `all` |

在命令行模式下可通过 `--sources=id1,id2,...` 限定下载的地图源。

## 工作流程
1. 将输入的矩形边界与缩放范围转换为瓦片坐标。
2. 为每个启用的地图源生成瓦片 URL，并加入线程池任务队列。
3. 跳过已存在的瓦片，只下载缺失的数据。
4. 持续输出进度；当某个源连续超时，会在当前运行中暂时禁用。

## 使用建议
- 在大规模下载前，先用较小区域和低级别做测试。
- 尊重服务限制，可通过调整线程数或缩放范围控制访问频率。
- 使用相同输出目录重复运行可实现断点续传（已下载文件会被跳过）。
- 如果需要新增地图源，只需在 `TILE_SOURCES` 中补充对应的 URL 模板和描述即可。
