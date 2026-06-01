(function (root, factory) {
    if (typeof define === 'function' && define.amd) {
        define([], factory);
    } else if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.watermark = factory();
    }
}(this, function () {
    var watermark = {};
    var defaultSettings = {
        watermark_id: 'wm_div_id',
        watermark_prefix: 'mask_div_id',
        watermark_txt: '测试水印',
        watermark_x: 20,
        watermark_y: 20,
        watermark_rows: 0,
        watermark_cols: 0,
        watermark_x_space: 50,
        watermark_y_space: 50,
        watermark_font: '微软雅黑',
        watermark_color: 'black',
        watermark_fontsize: '18px',
        watermark_alpha: 0.15,
        watermark_width: 100,
        watermark_height: 100,
        watermark_angle: 15,
        page_offsetTop: 0,
        page_offsetLeft: 0,
        watermark_parent_node: null,
        monitor: true
    };
    var currentSettings;
    var observer;

    function extendSettings(settings) {
        var source = settings || {};
        for (var key in source) {
            if (Object.prototype.hasOwnProperty.call(source, key) && (source[key] || source[key] === 0)) {
                defaultSettings[key] = source[key];
            }
        }
        defaultSettings.watermark_txt = normalizeWatermarkText(defaultSettings.watermark_txt);
        return defaultSettings;
    }

    function normalizeWatermarkText(text) {
        if (text === undefined || text === null) {
            return '';
        }
        return String(text).replace(/\\r\\n/g, '\n')
            .replace(/\\n/g, '\n')
            .replace(/\\r/g, '\n')
            .replace(/\r\n/g, '\n')
            .replace(/\r/g, '\n');
    }

    function numberValue(value, fallback) {
        var parsed = parseFloat(value);
        return isNaN(parsed) ? fallback : parsed;
    }

    function parseFontSize(value) {
        return numberValue(String(value || '').replace('px', ''), 18);
    }

    function removeExistingMark(settings) {
        var existing = document.getElementById(settings.watermark_id);
        if (existing && existing.parentNode) {
            existing.parentNode.removeChild(existing);
        }
    }

    function appendWatermarkText(container, lines) {
        for (var i = 0; i < lines.length; i++) {
            var line = document.createElement('div');
            line.appendChild(document.createTextNode(lines[i]));
            container.appendChild(line);
        }
    }

    function createContainer(parent, settings) {
        var mark = document.createElement('div');
        mark.id = settings.watermark_id;
        mark.setAttribute('style', 'pointer-events: none !important; display: block !important');
        var root = typeof mark.attachShadow === 'function' ? mark.attachShadow({ mode: 'open' }) : mark;
        var children = parent.children;
        var index = Math.floor(Math.random() * Math.max(children.length - 1, 0));
        if (children[index]) {
            parent.insertBefore(mark, children[index]);
        } else {
            parent.appendChild(mark);
        }
        return root;
    }

    function loadMark(settings) {
        settings = extendSettings(settings);
        if (observer) {
            observer.disconnect();
        }
        removeExistingMark(settings);

        var parentNode = document.getElementById(settings.watermark_parent_node);
        var parent = parentNode || document.body;
        var pageWidth = Math.max(parent.scrollWidth, parent.clientWidth);
        var pageHeight = Math.max(parent.scrollHeight, parent.clientHeight);
        var offsetTop = parent.offsetTop || 0;
        var offsetLeft = parent.offsetLeft || 0;

        if (settings.page_offsetTop || settings.page_offsetLeft) {
            settings.watermark_x = numberValue(settings.watermark_x, 0) + offsetLeft;
            settings.watermark_y = numberValue(settings.watermark_y, 0) + offsetTop;
        }

        var lines = normalizeWatermarkText(settings.watermark_txt).split('\n');
        var fontSize = parseFontSize(settings.watermark_fontsize);
        var lineHeight = Math.ceil(fontSize * 1.35);
        var watermarkWidth = numberValue(settings.watermark_width, 100);
        var watermarkHeight = Math.max(numberValue(settings.watermark_height, 100), lineHeight * Math.max(lines.length, 1));
        var xSpace = numberValue(settings.watermark_x_space, 50);
        var ySpace = numberValue(settings.watermark_y_space, 50);
        var startX = numberValue(settings.watermark_x, 0);
        var startY = numberValue(settings.watermark_y, 0);
        var cols = numberValue(settings.watermark_cols, 0);
        var rows = numberValue(settings.watermark_rows, 0);

        if (!cols) {
            cols = parseInt((pageWidth - startX) / (watermarkWidth + xSpace), 10);
            cols = Math.max(cols, 1);
        }
        if (!rows) {
            rows = parseInt((pageHeight - startY) / (watermarkHeight + ySpace), 10);
            rows = Math.max(rows, 1);
        }

        var allWidth = startX + watermarkWidth * cols + xSpace * Math.max(cols - 1, 0);
        var allHeight = startY + watermarkHeight * rows + ySpace * Math.max(rows - 1, 0);
        var root = createContainer(parent, settings);
        var fragment = document.createDocumentFragment();

        for (var i = 0; i < rows; i++) {
            var y = startY + (pageHeight - allHeight) / 2 + (watermarkHeight + ySpace) * i;
            if (parentNode) {
                y += offsetTop;
            }
            for (var j = 0; j < cols; j++) {
                var x = startX + (pageWidth - allWidth) / 2 + (watermarkWidth + xSpace) * j;
                if (parentNode) {
                    x += offsetLeft;
                }
                var mask = document.createElement('div');
                appendWatermarkText(mask, lines);
                mask.id = settings.watermark_prefix + i + j;
                mask.style.webkitTransform = 'rotate(-' + settings.watermark_angle + 'deg)';
                mask.style.MozTransform = 'rotate(-' + settings.watermark_angle + 'deg)';
                mask.style.msTransform = 'rotate(-' + settings.watermark_angle + 'deg)';
                mask.style.OTransform = 'rotate(-' + settings.watermark_angle + 'deg)';
                mask.style.transform = 'rotate(-' + settings.watermark_angle + 'deg)';
                mask.style.visibility = '';
                mask.style.position = 'absolute';
                mask.style.left = x + 'px';
                mask.style.top = y + 'px';
                mask.style.overflow = 'hidden';
                mask.style.zIndex = '9999999';
                mask.style.opacity = settings.watermark_alpha;
                mask.style.fontSize = settings.watermark_fontsize;
                mask.style.fontFamily = settings.watermark_font;
                mask.style.color = settings.watermark_color;
                mask.style.textAlign = 'center';
                mask.style.width = watermarkWidth + 'px';
                mask.style.height = watermarkHeight + 'px';
                mask.style.lineHeight = lineHeight + 'px';
                mask.style.display = 'flex';
                mask.style.flexDirection = 'column';
                mask.style.alignItems = 'center';
                mask.style.justifyContent = 'center';
                mask.style.pointerEvents = 'none';
                mask.style.userSelect = 'none';
                mask.style.whiteSpace = 'pre-line';
                mask.style.wordBreak = 'break-all';
                mask.style['-ms-user-select'] = 'none';
                fragment.appendChild(mask);
            }
        }
        root.appendChild(fragment);

        var monitor = settings.monitor === undefined ? defaultSettings.monitor : settings.monitor;
        if (monitor && window.MutationObserver) {
            observer = observer || new MutationObserver(function (records) {
                if (records && records.length) {
                    loadMark(currentSettings);
                }
            });
            observer.observe(parent, { childList: true, attributes: true, subtree: true });
            var watermarkNode = document.getElementById(defaultSettings.watermark_id);
            if (watermarkNode && watermarkNode.shadowRoot) {
                observer.observe(watermarkNode.shadowRoot, { childList: true, attributes: true, subtree: true });
            }
        }
    }

    function removeMark() {
        if (observer) {
            observer.disconnect();
        }
        removeExistingMark(defaultSettings);
    }

    watermark.init = function (settings) {
        currentSettings = settings;
        loadMark(settings);
        window.addEventListener('load', function () {
            loadMark(settings);
        });
        window.addEventListener('resize', function () {
            loadMark(settings);
        });
    };

    watermark.load = function (settings) {
        currentSettings = settings;
        loadMark(settings);
    };

    watermark.remove = function () {
        removeMark();
    };

    return watermark;
}));
