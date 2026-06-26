<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8" />
    <title>${file.name}</title>
    <link rel='stylesheet' href='xlsx/plugins/css/pluginsCss.css' />
    <link rel='stylesheet' href='xlsx/plugins/plugins.css' />
    <link rel='stylesheet' href='xlsx/css/luckysheet.css' />
    <link rel='stylesheet' href='xlsx/assets/iconfont/iconfont.css' />
    <script src="xlsx/plugins/js/plugin.js"></script>
    <script src="xlsx/luckysheet.umd.js"></script>
    <script src="js/watermark.js" type="text/javascript"></script>
    <script src="js/base64.min.js" type="text/javascript"></script>
</head>
<#if pdfUrl?contains("http://") || pdfUrl?contains("https://") || pdfUrl?contains("ftp://")>
    <#assign finalUrl="${pdfUrl}">
<#else>
    <#assign finalUrl="${baseUrl}${pdfUrl}">
</#if>
<script>
    /**
     * 初始化水印
     */
    function initWaterMark(parentNode) {
        let watermarkTxt = '${watermarkTxt?js_string}';
        let watermarkFont = '${watermarkFont?js_string}';
        let watermarkFontsize = '${watermarkFontsize?js_string}';
        let watermarkColor = '${watermarkColor?js_string}';
        if (watermarkTxt !== '') {
            if (parentNode === 'luckysheet-cell-main') {
                initLuckysheetWaterMark({
                    text: watermarkTxt,
                    font: watermarkFont,
                    fontSize: watermarkFontsize,
                    color: watermarkColor,
                    alpha: ${watermarkAlpha},
                    width: ${watermarkWidth},
                    height: ${watermarkHeight},
                    xSpace: ${watermarkXSpace},
                    ySpace: ${watermarkYSpace},
                    angle: ${watermarkAngle}
                });
                return;
            }
            let watermarkOptions = {
                watermark_txt: watermarkTxt,
                watermark_x: 0,
                watermark_y: 0,
                watermark_rows: 0,
                watermark_cols: 0,
                watermark_x_space: ${watermarkXSpace},
                watermark_y_space: ${watermarkYSpace},
                watermark_font: watermarkFont,
                watermark_fontsize: watermarkFontsize,
                watermark_color: watermarkColor,
                watermark_alpha: ${watermarkAlpha},
                watermark_width: ${watermarkWidth},
                watermark_height: ${watermarkHeight},
                watermark_angle: ${watermarkAngle},
            };
            if (parentNode) {
                watermarkOptions.watermark_parent_node = parentNode;
            }
            watermarkOptions.monitor = false;
            watermark.init(watermarkOptions);
        }
    }

    function initLuckysheetWaterMark(options) {
        var container = document.getElementById('luckysheet');
        if (!container || !options.text) {
            return;
        }
        var layer = document.getElementById('xlsx-watermark-layer');
        var cellMain = document.getElementById('luckysheet-cell-main');
        if (!layer) {
            layer = document.createElement('div');
            layer.id = 'xlsx-watermark-layer';
            layer.style.position = 'absolute';
            layer.style.pointerEvents = 'none';
            layer.style.zIndex = '9999999';
            layer.style.overflow = 'hidden';
            container.appendChild(layer);
        }
        updateLuckysheetWaterMarkBounds(layer, cellMain, container);
        layer.style.backgroundImage = 'url("' + createLuckysheetWatermarkPattern(options) + '")';
        layer.style.backgroundRepeat = 'repeat';
        layer.style.backgroundPosition = '0 0';

        bindLuckysheetWaterMarkScroll(layer, cellMain, container);
    }

    function updateLuckysheetWaterMarkBounds(layer, cellMain, container) {
        if (!cellMain) {
            layer.style.left = '0';
            layer.style.top = '0';
            layer.style.right = '0';
            layer.style.bottom = '0';
            return;
        }
        layer.style.left = cellMain.offsetLeft + 'px';
        layer.style.top = cellMain.offsetTop + 'px';
        layer.style.width = cellMain.clientWidth + 'px';
        layer.style.height = cellMain.clientHeight + 'px';
        layer.style.right = 'auto';
        layer.style.bottom = 'auto';
        if (container && window.getComputedStyle(container).position === 'static') {
            container.style.position = 'relative';
        }
    }

    function createLuckysheetWatermarkPattern(options) {
        var lines = String(options.text || '').split(/\r\n|\r|\n/);
        var fontSize = parseInt(String(options.fontSize || '18px').replace(/px$/i, ''), 10) || 18;
        var lineHeight = Math.ceil(fontSize * 1.35);
        var itemWidth = parseInt(options.width, 10) || 100;
        var itemHeight = Math.max(parseInt(options.height, 10) || 100, lineHeight * Math.max(lines.length, 1));
        var canvas = document.createElement('canvas');
        var ratio = window.devicePixelRatio || 1;
        var tileWidth = itemWidth + (parseInt(options.xSpace, 10) || 50);
        var tileHeight = itemHeight + (parseInt(options.ySpace, 10) || 50);
        var context;

        canvas.width = Math.max(tileWidth * ratio, 1);
        canvas.height = Math.max(tileHeight * ratio, 1);
        canvas.style.width = tileWidth + 'px';
        canvas.style.height = tileHeight + 'px';
        context = canvas.getContext('2d');
        context.scale(ratio, ratio);
        context.clearRect(0, 0, tileWidth, tileHeight);
        context.globalAlpha = Number(options.alpha) || 0.15;
        context.fillStyle = options.color || 'black';
        context.font = fontSize + 'px ' + (options.font || 'Microsoft YaHei');
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        context.translate(tileWidth / 2, tileHeight / 2);
        context.rotate(-(Number(options.angle) || 0) * Math.PI / 180);
        lines.forEach(function(line, index) {
            context.fillText(line, 0, (index - (lines.length - 1) / 2) * lineHeight);
        });
        return canvas.toDataURL('image/png');
    }

    function bindLuckysheetWaterMarkScroll(layer, cellMain, container) {
        var scrollX = document.getElementById('luckysheet-scrollbar-x');
        var scrollY = document.getElementById('luckysheet-scrollbar-y');
        var scheduled = false;

        function updatePosition() {
            scheduled = false;
            updateLuckysheetWaterMarkBounds(layer, cellMain, container);
            var left = scrollX ? scrollX.scrollLeft : 0;
            var top = scrollY ? scrollY.scrollTop : 0;
            layer.style.backgroundPosition = (-left) + 'px ' + (-top) + 'px';
        }

        function requestUpdate() {
            if (scheduled) {
                return;
            }
            scheduled = true;
            (window.requestAnimationFrame || window.setTimeout)(updatePosition);
        }

        if (layer.__xlsxWatermarkScrollBound) {
            updatePosition();
            return;
        }
        layer.__xlsxWatermarkScrollBound = true;
        if (scrollX) {
            scrollX.addEventListener('scroll', requestUpdate, { passive: true });
        }
        if (scrollY) {
            scrollY.addEventListener('scroll', requestUpdate, { passive: true });
        }
        window.addEventListener('resize', requestUpdate);
        updatePosition();
    }

    // 添加加载状态管理
    let isLoading = false;
    const xlsxAllowEdit = ${(xlsxallowEdit!false)?string('true','false')};
    const xlsxShowToolbar = ${xlsxshowtoolbar?string('true','false')};

    function preventReadonlySpreadsheetActions(event) {
        if (xlsxAllowEdit) {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        return false;
    }

    function preventReadonlySpreadsheetShortcuts(event) {
        if (xlsxAllowEdit || !(event.ctrlKey || event.metaKey)) {
            return;
        }
        var key = String(event.key || '').toLowerCase();
        if (['a', 'c', 'p', 's', 'v', 'x'].indexOf(key) > -1) {
            preventReadonlySpreadsheetActions(event);
        }
    }

    if (!xlsxAllowEdit) {
        document.addEventListener('copy', preventReadonlySpreadsheetActions, true);
        document.addEventListener('cut', preventReadonlySpreadsheetActions, true);
        document.addEventListener('paste', preventReadonlySpreadsheetActions, true);
        document.addEventListener('contextmenu', preventReadonlySpreadsheetActions, true);
        document.addEventListener('keydown', preventReadonlySpreadsheetShortcuts, true);
    }

</script>
<style>
    * {
        margin: 0;
        padding: 0;
    }

    html, body {
        height: 100%;
        width: 100%;
        overflow: hidden;
    }

    #loading-overlay {
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(255, 255, 255, 0.95);
        display: flex;
        justify-content: center;
        align-items: center;
        flex-direction: column;
        z-index: 9999;
        transition: opacity 0.3s ease;
    }

    #loading-progress {
        width: 300px;
        height: 20px;
        background: #f0f0f0;
        border-radius: 10px;
        margin-top: 20px;
        overflow: hidden;
    }

    #loading-bar {
        width: 0%;
        height: 100%;
        background: linear-gradient(90deg, #4CAF50, #8BC34A);
        transition: width 0.3s ease;
        border-radius: 10px;
    }

    .spinner {
        width: 50px;
        height: 50px;
        border: 5px solid #f3f3f3;
        border-top: 5px solid #4CAF50;
        border-radius: 50%;
        animation: spin 1s linear infinite;
    }

    @keyframes spin {
        0% { transform: rotate(0deg); }
        100% { transform: rotate(360deg); }
    }

    .loading-text {
        margin-top: 20px;
        font-size: 16px;
        color: #666;
    }

    .error-message {
        display: none;
        background: #ffebee;
        border: 1px solid #ffcdd2;
        border-radius: 4px;
        padding: 20px;
        margin: 20px;
        text-align: center;
    }

</style>
<body>
<!-- 添加加载遮罩层 -->
<div id="loading-overlay">
    <div class="spinner"></div>
    <div class="loading-text">正在加载Excel文件...</div>
    <div id="loading-progress">
        <div id="loading-bar"></div>
    </div>
</div>

<!-- 错误提示 -->
<div id="error-message" class="error-message">
    <h3>加载失败</h3>
    <p id="error-detail"></p>
    <button onclick="retryLoad()" style="margin-top: 10px; padding: 8px 16px;">重试</button>
</div>

<div id="lucky-mask-demo" style="position: absolute;z-index: 1000000;left: 0px;top: 0px;bottom: 0px;right: 0px; background: rgba(255, 255, 255, 0.8); text-align: center;font-size: 40px;align-items:center;justify-content: center;display: none;">加载中</div>

<p style="text-align:center;">
<div id="button-area" style="display: none;">
    <#if xlsxallowEdit>
    <button id="confirm-button" onclick="print()">打印</button>
    </#if>
</div>
<div id="luckysheet" style="margin:0px;padding:0px;position:absolute;width:100%;left: 0px;top: 20px;bottom: 0px;outline: none;"></div>

<script src="xlsx/luckyexcel.umd.js"></script>
<script>
    var url = '${finalUrl}';
   	var kkagent = '${kkagent}';
    var baseUrl = '${baseUrl}'.endsWith('/') ? '${baseUrl}' : '${baseUrl}' + '/';
    if (kkagent === 'true' || !url.startsWith(baseUrl)) {
        url = baseUrl + 'getCorsFile?urlPath=' + encodeURIComponent(Base64.encode(url))+ "&key=${kkkey}";
    }

    let mask = document.getElementById("lucky-mask-demo");
    let loadingOverlay = document.getElementById("loading-overlay");
    let loadingBar = document.getElementById("loading-bar");
    let loadingText = document.querySelector(".loading-text");
    let errorMessage = document.getElementById("error-message");

    // 更新加载进度
    function updateProgress(percent, message) {
        if (loadingBar) {
            var normalizedPercent = Math.max(0, Math.min(100, Math.round(percent)));
            loadingBar.style.width = normalizedPercent + '%';
        }
        if (loadingText && message) {
            loadingText.textContent = message;
        }
    }

    function formatFileSize(bytes) {
        if (!bytes || bytes < 0) {
            return '';
        }
        if (bytes < 1024) {
            return bytes + 'B';
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024).toFixed(1) + 'KB';
        }
        return (bytes / 1024 / 1024).toFixed(1) + 'MB';
    }

    function updateDownloadProgress(loaded, total) {
        var startPercent = 30;
        var downloadPercent = 40;
        if (total > 0) {
            var percent = Math.min(100, loaded / total * 100);
            updateProgress(startPercent + downloadPercent * percent / 100, '正在下载Excel文件... ' + Math.round(percent) + '% (' + formatFileSize(loaded) + '/' + formatFileSize(total) + ')');
            return;
        }
        updateProgress(startPercent, '正在下载Excel文件... ' + formatFileSize(loaded));
    }

    // 显示错误信息
    function showError(message) {
        hideLoading();
        errorMessage.style.display = 'block';
        document.getElementById('error-detail').textContent = message;
    }

    // 隐藏加载动画
    function hideLoading() {
        if (loadingOverlay) {
            loadingOverlay.style.opacity = '0';
            setTimeout(() => {
                loadingOverlay.style.display = 'none';
                document.getElementById('button-area').style.display = 'block';
            }, 300);
        }
    }

    // 重试加载
    function retryLoad() {
        errorMessage.style.display = 'none';
        loadingOverlay.style.display = 'flex';
        loadingOverlay.style.opacity = '1';
        loadTextAsync();
    }

    // 异步加载Excel文件
    async function loadTextAsync() {
        if (isLoading) return;
        
        isLoading = true;
        updateProgress(10, '正在准备Excel文件...');
        
        try {
            const value = url;
            const name = '${file.name?js_string}';
            
            if (!value) {
                showError('文件URL为空');
                return;
            }

            updateProgress(30, '正在下载Excel文件...');
            
            await new Promise(resolve => setTimeout(resolve, 100)); // 给UI更新一点时间
            var exportJson = await transformExcel(value, name);

            updateProgress(85, '正在渲染表格...');
            await createLuckysheet(exportJson);
            
            updateProgress(100, '加载完成');
            
            // 延迟隐藏加载界面，让用户看到加载完成
            setTimeout(() => {
                hideLoading();
                isLoading = false;
            }, 500);
            
        } catch (error) {
            console.error('加载Excel失败:', error);
            showError('加载失败: ' + error.message);
            isLoading = false;
        }
    }

    function transformExcel(value, name) {
        if (!window.Worker) {
            return transformOnMainThread(value, name);
        }
        return transformWithWorker(value, name).catch(function (error) {
            console.warn('Excel Worker转换失败，降级到主线程:', error);
            return transformOnMainThread(value, name);
        });
    }

    function transformWithWorker(value, name) {
        return new Promise((resolve, reject) => {
            var worker;
            try {
                worker = new Worker('xlsx/luckyexcel-worker.js?v=' + Date.now());
            } catch (error) {
                reject(error);
                return;
            }

            var settled = false;

            function finish(callback, value) {
                if (settled) {
                    return;
                }
                settled = true;
                worker.terminate();
                callback(value);
            }

            worker.onmessage = function (event) {
                var data = event.data || {};
                if (data.type === 'success') {
                    finish(resolve, data.exportJson);
                    return;
                }
                if (data.type === 'progress') {
                    updateDownloadProgress(data.loaded, data.total);
                    return;
                }
                if (data.type === 'stage') {
                    updateProgress(75, data.message || '正在转换Excel文件...');
                    return;
                }
                if (data.type === 'error') {
                    finish(reject, new Error(data.message || 'Excel转换失败'));
                }
            };

            worker.onerror = function (error) {
                finish(reject, error);
            };

            worker.postMessage({
                url: value,
                name: name
            });
        });
    }

    function readArrayBufferOnMainThread(value) {
        return new Promise((resolve, reject) => {
            var xhr = new XMLHttpRequest();
            xhr.open('GET', value, true);
            xhr.responseType = 'arraybuffer';
            xhr.onprogress = function(event) {
                updateDownloadProgress(event.loaded, event.lengthComputable ? event.total : 0);
            };
            xhr.onreadystatechange = function() {
                if (xhr.readyState !== 4) {
                    return;
                }
                if (xhr.status === 200 || xhr.status === 0) {
                    updateDownloadProgress(xhr.response ? xhr.response.byteLength : 0, xhr.response ? xhr.response.byteLength : 0);
                    resolve(xhr.response);
                } else {
                    reject(new Error('Ajax error for ' + value + ' : ' + xhr.status + ' ' + xhr.statusText));
                }
            };
            xhr.onerror = function() {
                reject(new Error('读取Excel文件失败'));
            };
            xhr.send();
        });
    }

    function createExcelFile(buffer, name) {
        var fileName = name || 'excel.xlsx';
        if (typeof File === 'function') {
            return new File([buffer], fileName, {
                type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
            });
        }
        var blob = new Blob([buffer], {
            type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        });
        blob.name = fileName;
        return blob;
    }

    function transformOnMainThread(value, name) {
        return new Promise((resolve, reject) => {
            readArrayBufferOnMainThread(value)
                .then(function(buffer) {
                    updateProgress(75, '正在转换Excel文件...');
                    LuckyExcel.transformExcelToLucky(createExcelFile(buffer, name), function(exportJson, luckysheetfile) {
                        if (!exportJson || !exportJson.sheets || exportJson.sheets.length === 0) {
                            reject(new Error("读取excel文件内容失败!"));
                            return;
                        }
                        resolve(exportJson);
                    }, undefined, function(error) {
                        reject(error);
                    });
                })
                .catch(function(error) {
                    reject(error);
                });
        });
    }

    function createLuckysheet(exportJson) {
        return new Promise((resolve, reject) => {
            requestAnimationFrame(() => {
                try {
                    window.luckysheet.destroy();
                    window.luckysheet.create({
                        container: 'luckysheet',
                        lang: "zh",
                        showtoolbarConfig:{
                            image: xlsxAllowEdit,
                            print: xlsxAllowEdit,
                            exportXlsx: xlsxAllowEdit,
                        },
                        allowCopy: xlsxAllowEdit, // 是否允许拷贝
                        showtoolbar: xlsxAllowEdit && xlsxShowToolbar,  // 是否显示工具栏
                        showinfobar: false, // 是否显示顶部信息栏
                        // myFolderUrl: "/",//作用：左上角<返回按钮的链接
                        showsheetbar: true, // 是否显示底部sheet页按钮
                        showstatisticBar: true, // 是否显示底部计数栏
                        sheetBottomConfig: xlsxAllowEdit, // sheet页下方的添加行按钮和回到顶部按钮配置
                        allowEdit: xlsxAllowEdit,// 是否允许前台编辑
                        enableAddRow: false, // 允许增加行
                        enableAddCol: false, // 允许增加列
                        userInfo: false, // 右上角的用户信息展示样式
                        showRowBar: true, // 是否显示行号区域
                        showColumnBar: false, // 是否显示列号区域
                        sheetFormulaBar: false, // 是否显示公式栏
                        enableAddBackTop: xlsxAllowEdit,//返回头部按钮
                        forceCalculation: false, //下面是导出插件 默认关闭
                        cellRightClickConfig: {
                            copy: xlsxAllowEdit,
                            copyAs: xlsxAllowEdit,
                            paste: xlsxAllowEdit,
                            insertRow: xlsxAllowEdit,
                            insertColumn: xlsxAllowEdit,
                            deleteRow: xlsxAllowEdit,
                            deleteColumn: xlsxAllowEdit,
                            deleteCell: xlsxAllowEdit,
                            hideRow: xlsxAllowEdit,
                            hideColumn: xlsxAllowEdit,
                            rowHeight: xlsxAllowEdit,
                            columnWidth: xlsxAllowEdit,
                            clear: xlsxAllowEdit,
                            matrix: xlsxAllowEdit,
                            sort: xlsxAllowEdit,
                            filter: xlsxAllowEdit,
                            chart: xlsxAllowEdit,
                            image: xlsxAllowEdit,
                            link: xlsxAllowEdit,
                            data: xlsxAllowEdit,
                            cellFormat: xlsxAllowEdit
                        },
                        sheetRightClickConfig: {
                            delete: xlsxAllowEdit,
                            copy: xlsxAllowEdit,
                            rename: xlsxAllowEdit,
                            color: xlsxAllowEdit,
                            hide: xlsxAllowEdit,
                            move: xlsxAllowEdit
                        },
                        data: exportJson.sheets,
                        title: exportJson.info.name,
                        userInfo: exportJson.info.name.creator,
                        // 添加加载完成的回调
                        hook: {
                            workbookCreateAfter: function() {
                                setTimeout(function() {
                                    initWaterMark('luckysheet-cell-main');
                                }, 0);
                                resolve();
                            }
                        }
                    });

                    updateProgress(90, '正在渲染表格...');
                    
                } catch (err) {
                    reject(err);
                }
            });
        });
    }

    // 页面加载完成后开始异步加载
    document.addEventListener('DOMContentLoaded', function() {
        // 延迟一点时间开始加载，确保DOM完全加载
        setTimeout(() => {
            loadTextAsync();
        }, 100);
    });

    // 添加取消加载的功能（按ESC键）
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' && isLoading) {
            // 可以在这里添加取消加载的逻辑
            console.log('用户取消了加载');
        }
    });
</script>
</body>
</html>
