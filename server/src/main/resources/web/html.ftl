<!DOCTYPE html>

<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, user-scalable=yes, initial-scale=1.0">
    <title>${file.name}</title>
    <#include "*/commonHeader.ftl">
    <#include "*/needFilePasswordHeader.ftl">
    <#assign readonlySpreadsheet = file.suffix?? && (file.suffix?lower_case == "xls" || file.suffix?lower_case == "xlsx")>
    <#if readonlySpreadsheet>
    <style>
        iframe {
            user-select: none;
        }
    </style>
    </#if>
</head>
<body>
<iframe id="officePreviewFrame" src="${pdfUrl}" width="100%" frameborder="0"<#if readonlySpreadsheet> onload="lockSpreadsheetFrame(this)"</#if>></iframe>
</body>

<script type="text/javascript">
    needFilePassword();
</script>

<script type="text/javascript">
    var officePreviewFrame = document.getElementById('officePreviewFrame');
    officePreviewFrame.height = document.documentElement.clientHeight - 10;
    <#if readonlySpreadsheet>
    function preventReadonlySpreadsheetActions(event) {
        event.preventDefault();
        event.stopPropagation();
        return false;
    }

    function preventReadonlySpreadsheetShortcuts(event) {
        if (!(event.ctrlKey || event.metaKey)) {
            return;
        }
        var key = String(event.key || '').toLowerCase();
        if (['a', 'c', 'p', 's', 'v', 'x'].indexOf(key) > -1) {
            preventReadonlySpreadsheetActions(event);
        }
    }

    function bindReadonlySpreadsheetGuards(targetDocument) {
        if (!targetDocument) {
            return;
        }
        targetDocument.addEventListener('copy', preventReadonlySpreadsheetActions, true);
        targetDocument.addEventListener('cut', preventReadonlySpreadsheetActions, true);
        targetDocument.addEventListener('paste', preventReadonlySpreadsheetActions, true);
        targetDocument.addEventListener('contextmenu', preventReadonlySpreadsheetActions, true);
        targetDocument.addEventListener('keydown', preventReadonlySpreadsheetShortcuts, true);
        if (targetDocument.documentElement) {
            targetDocument.documentElement.style.userSelect = 'none';
        }
        if (targetDocument.body) {
            targetDocument.body.style.userSelect = 'none';
        }
    }

    function lockSpreadsheetFrame(frame) {
        try {
            bindReadonlySpreadsheetGuards(frame.contentDocument || frame.contentWindow.document);
        } catch (error) {
            // Same-origin preview content can be locked through the iframe document.
        }
    }
    </#if>
    /**
     * 页面变化调整高度
     */
    window.onresize = function () {
        var fm = document.getElementById("officePreviewFrame");
        fm.height = window.document.documentElement.clientHeight - 10;
    }
    /*初始化水印*/
    window.onload = function () {
        initWaterMark();
    }
</script>
</html>
