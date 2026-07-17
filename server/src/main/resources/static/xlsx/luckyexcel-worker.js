(function () {
    'use strict';

    var workerGlobal = self;
    // Worker 原生自带只读的 navigator，无需也不能重新赋值；ActiveXObject 本就不存在，
    // 访问未定义属性天然为 undefined。只需让 window 指向 self 供 UMD bundle 使用。
    workerGlobal.window = workerGlobal.window || workerGlobal;

    importScripts('./luckyexcel.umd.js');

    function postError(message) {
        workerGlobal.postMessage({
            type: 'error',
            message: message || 'Excel转换失败'
        });
    }

    function readArrayBufferByXhr(url, resolve, reject) {
        var xhr = new workerGlobal.XMLHttpRequest();
        xhr.open('GET', url, true);
        xhr.responseType = 'arraybuffer';
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== 4) {
                return;
            }
            if (xhr.status === 200 || xhr.status === 0) {
                resolve(xhr.response);
            } else {
                reject(new Error('Ajax error for ' + url + ' : ' + xhr.status + ' ' + xhr.statusText));
            }
        };
        xhr.onerror = function () {
            reject(new Error('读取Excel文件失败'));
        };
        xhr.send();
    }

    function readArrayBuffer(url) {
        return new Promise(function (resolve, reject) {
            if (workerGlobal.XMLHttpRequest) {
                readArrayBufferByXhr(url, resolve, reject);
                return;
            }
            if (workerGlobal.fetch) {
                workerGlobal.fetch(url, {credentials: 'same-origin'})
                    .then(function (response) {
                        if (!response.ok) {
                            throw new Error('Ajax error for ' + url + ' : ' + response.status + ' ' + response.statusText);
                        }
                        return response.arrayBuffer();
                    })
                    .then(resolve)
                    .catch(reject);
                return;
            }
            reject(new Error('当前浏览器不支持Worker内读取Excel文件'));
        });
    }

    function createExcelFile(buffer, name) {
        var fileName = name || 'excel.xlsx';
        if (typeof workerGlobal.File === 'function') {
            return new workerGlobal.File([buffer], fileName, {
                type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
            });
        }
        var blob = new workerGlobal.Blob([buffer], {
            type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        });
        blob.name = fileName;
        return blob;
    }

    workerGlobal.onmessage = function (event) {
        var data = event.data || {};
        var url = data.url;
        var name = data.name;

        if (!url) {
            postError('文件URL为空');
            return;
        }

        readArrayBuffer(url)
            .then(function (buffer) {
                var excelFile = createExcelFile(buffer, name);
                LuckyExcel.transformExcelToLucky(
                    excelFile,
                    function (exportJson) {
                        if (!exportJson || !exportJson.sheets || exportJson.sheets.length === 0) {
                            postError('读取excel文件内容失败!');
                            return;
                        }
                        workerGlobal.postMessage({
                            type: 'success',
                            exportJson: exportJson
                        });
                    },
                    undefined,
                    function (error) {
                        postError(error && error.message ? error.message : String(error));
                    }
                );
            })
            .catch(function (error) {
                postError(error && error.message ? error.message : String(error));
            });
    };
}());
