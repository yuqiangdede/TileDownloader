# TileDownloader

批量下载地图瓦片的小工具，支持在设定的经纬度范围内、多个缩放级别并发抓取在线瓦片服务数据。适合需要离线缓存 OpenStreetMap、ArcGIS、Gaode（高德）、Google、OpenSeaMap 等在线底图的场景。

## 功能概览
- 按照经纬度范围自动换算瓦片 `x/y` 区间并循环下载
- 支持 `ZXY`（OpenStreetMap、Gaode、Google 等）与 `ZYX`（ArcGIS）两种 URL 模式
- 跳过已存在的瓦片文件，避免重复写入
- 线程池并发抓取，定期输出进度与最后一个文件路径
- 支持预置地图源分组（`DOWNLOAD_TYPE`）或命令行筛选（`--sources`），灵活组合多个服务
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
   程序默认下载 `DOWNLOAD_TYPE` 指定的瓦片源（默认 `all`，即全部源），并存储到 `D:\temp` 目录下。

> 注意：使用前请确认目标瓦片服务允许批量下载，并遵守服务方的使用政策与限速要求。

## 支持的瓦片服务
源码中已经预置常见地图源，便于开箱即用。可通过 `DOWNLOAD_TYPE` 指定默认下载的分组，也可以在运行时使用 `--sources=` 精确选择。

### OpenStreetMap 系列（ZXY）
| ID | 名称 | 示例 URL |
| --- | --- | --- |
| `osm-standard` | OSM Standard | `https://tile.openstreetmap.org/14/13594/6447.png` |
| `osm-hot` | OSM HOT（人道主义，自动均衡 `a/b/c`） | `https://tile-{a,b,c}.openstreetmap.fr/hot/12/3399/1612.png` |
| `cyclosm` | CyclOSM（自动均衡 `a/b/c`） | `https://{a,b,c}.tile-cyclosm.openstreetmap.fr/cyclosm/12/3400/1612.png` |

### ArcGIS 系列（ZYX）
| ID | 名称 | 示例 URL |
| --- | --- | --- |
| `arcgis-topo` | ArcGIS Topographic | `https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/11/783/1700` |
| `arcgis-imagery` | ArcGIS Imagery | `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/13/3122/6801` |

### OpenSeaMap（ZXY）
| ID | 名称 | 示例 URL |
| --- | --- | --- |
| `seamap-base` | OpenSeaMap Base | `https://t2.openseamap.org/tile/14/13594/6447.png` |
| `seamap-seamark` | OpenSeaMap Seamarks | `https://tiles.openseamap.org/seamark/14/13553/6263.png` |

### Gaode（高德，ZXY）
基础 URL 示例：`https://webst0{1-4}.is.autonavi.com/appmaptile?style=6&x=13594&y=6447&z=14`

`style` 参数说明：

| ID | 名称 | style | 说明 |
| --- | --- | --- | --- |
| `gaode-satellite` | Gaode Satellite | `6` | 卫星图 |
| `gaode-hybrid` | Gaode Hybrid | `7` | 矢量路网混合图 |
| `gaode-roadnet` | Gaode Roadnet | `8` | 纯路网 |
| `gaode-light` | Gaode Light High POI | `9` | 浅色混合图（地点标注多） |
| `gaode-light-poi` | Gaode Light Low POI | `10` | 浅色混合图（地点标注少） |

### Google（谷歌，ZXY）
基础 URL 示例：`https://mt3.google.com/vt/lyrs=m&x=13594&y=6447&z=14`

`lyrs` 参数说明：

| ID | 名称 | lyrs | 说明 |
| --- | --- | --- | --- |
| `google-vector` | Google Vector | `m` | 普通矢量地图（路网、地名） |
| `google-satellite` | Google Satellite | `s` | 卫星影像（无标注） |
| `google-hybrid` | Google Hybrid | `y` | 卫星影像 + 标注（等价于 s + roads） |
| `google-terrain` | Google Terrain | `t` | 地形图 |
| `google-terrain-labels` | Google Terrain Labels | `p` | 地形图 + 标注 |
| `google-roads` | Google Roads Overlay | `h` | 仅路网（Hybrid 叠加层） |

## 关键参数
`TileDownloader` 顶层常量控制下载行为，修改后需重新编译。

| 常量 | 说明 | 默认值 |
| --- | --- | --- |
| `BASE_DIRECTORY` | 本地保存路径 | `D:\temp` |
| `START_LON` / `END_LON` | 经度范围（西→东） | `117` / `118` |
| `START_LAT` / `END_LAT` | 纬度范围（南→北） | `38` / `39` |
| `MIN_ZOOM` / `MAX_ZOOM` | 缩放层级范围 | `0` / `12` |
| `THREADS` | 并发下载线程数 | `16` |
| `START` | 跳过前若干瓦片（容错重跑） | `0` |
| `PROGRESS_STEP` / `LARGE_PROGRESS_STEP` | 进度日志频率 | `100` / `10_000` |
| `BUFFER_SIZE` | 下载写入缓冲区大小（字节） | `8192` |
| `DOWNLOAD_TYPE` | 默认启用的瓦片源分组（支持 `all`、`osm`、`gaode`、`google` 等） | `all` |
| `TILE_SOURCES` | 预置的瓦片源列表，可按需增删 | 内置多项 |

运行时可通过 `--sources=` 指定源，例如：

```powershell
java -cp target/MapDownloader-1.0-SNAPSHOT.jar com.yuqiangdede.TileDownloader --sources=gaode-satellite,google-hybrid
```

## 工作流程简述
1. 根据设定的经纬度、缩放层级计算瓦片编号范围。
2. 按瓦片循环构建 URL，并提交到固定大小的线程池。
3. 下载前检测本地文件是否存在且非空，避免重复请求。
4. 请求瓦片时设定连接与读取超时；保存成功后按配置输出进度和日志。

## 常见定制
- **切换底图**：编辑 `TILE_SOURCES` 数组，启用/禁用或新增服务，并配合 `DOWNLOAD_TYPE` 控制默认集合。
- **限制下载范围**：收紧经纬度或缩放层级，减少请求数量。
- **控制并发**：调整 `THREADS`，在速度与服务端限流之间平衡。
- **断点续传**：运行中断后，将 `START` 设置为已成功瓦片数量，继续执行即可。

## 备注
- 瓦片服务器通常对批量抓取存在限制，建议遵守服务条款、合理设置线程数，并注意限速。
- 若要添加新的地图服务，可参考现有 `TILE_SOURCES` 的构造方式，设置好 URL 模板、模式以及输出目录。
### 图形界面（可选）
想通过可视化方式调整经纬度和地图源，可启动 Swing 界面：

```powershell
java -cp target/MapDownloader-1.0-SNAPSHOT.jar com.yuqiangdede.TileDownloaderUI
```

界面内支持：

- 选择输出目录、经纬度范围、缩放等级及并发线程数
- 通过下拉框选择预置下载类型，或切换为手动多选地图源
- 可选开启 SOCKS 代理（默认主机 `127.0.0.1`，端口可自定义）
- 在同一窗口查看实时日志输出
