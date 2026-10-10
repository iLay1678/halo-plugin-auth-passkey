(function () {
    function init() {
        if (!document.getElementById("login-form")) return;

        var params = new URLSearchParams(window.location.search);
        var rememberMe = document.getElementById("remember-me");
        if (rememberMe && params.has("remember-me")) {
            rememberMe.checked = params.get("remember-me") === "true";
        }

        function updateLinks() {
            document.querySelectorAll('.pill-items a[href]').forEach(function (link) {
                var target = new URL(link.href, window.location.href);
                if (target.origin !== window.location.origin || target.pathname !== window.location.pathname
                    || !target.searchParams.has("method")) return;

                var method = target.searchParams.get("method");
                // 嵌套的 OIDC 回跳 URL 只解码、编码一次，保留其中的 +、% 和 &。
                target.search = window.location.search;
                target.searchParams.set("method", method);
                if (rememberMe) {
                    target.searchParams.set("remember-me", String(rememberMe.checked));
                }
                link.href = target.pathname + target.search + target.hash;
            });
        }

        updateLinks();
        if (rememberMe) rememberMe.addEventListener("change", updateLinks);
        document.addEventListener("click", updateLinks, true);
        document.addEventListener("auxclick", updateLinks, true);
        window.addEventListener("pageshow", updateLinks);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
