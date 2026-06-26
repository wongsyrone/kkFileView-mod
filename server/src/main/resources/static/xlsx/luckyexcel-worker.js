(function () {
    'use strict';

    var workerGlobal = self;
    workerGlobal.window = workerGlobal.window || workerGlobal;
    if (!workerGlobal.window.navigator) {
        try {
            Object.defineProperty(workerGlobal.window, 'navigator', {
                value: {userAgent: 'kkFileViewWorker'},
                configurable: true
            });
        } catch (ignore) {
        }
    }
    try {
        workerGlobal.window.devicePixelRatio = workerGlobal.window.devicePixelRatio || 1;
        workerGlobal.window.ActiveXObject = undefined;
    } catch (ignore) {
    }

    importScripts('./luckyexcel.umd.js');

    function postError(message) {
        workerGlobal.postMessage({
            type: 'error',
            message: message || 'Excel转换失败'
        });
    }

    function postProgress(loaded, total) {
        workerGlobal.postMessage({
            type: 'progress',
            loaded: loaded || 0,
            total: total || 0
        });
    }

    function readArrayBufferByXhr(url, resolve, reject) {
        var xhr = new workerGlobal.XMLHttpRequest();
        xhr.open('GET', url, true);
        xhr.responseType = 'arraybuffer';
        xhr.onprogress = function (event) {
            postProgress(event.loaded, event.lengthComputable ? event.total : 0);
        };
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== 4) {
                return;
            }
            if (xhr.status === 200 || xhr.status === 0) {
                postProgress(xhr.response ? xhr.response.byteLength : 0, xhr.response ? xhr.response.byteLength : 0);
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
                    .then(function (buffer) {
                        postProgress(buffer ? buffer.byteLength : 0, buffer ? buffer.byteLength : 0);
                        resolve(buffer);
                    })
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
                workerGlobal.postMessage({
                    type: 'stage',
                    message: '正在转换Excel文件...'
                });
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
