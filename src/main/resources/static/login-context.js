(function () {
    var API = "/apis/api.passkey.halo.run/v1alpha1";
    var authenticating = false;
    var authenticationOptions = null;
    var authenticationOptionsPromise = null;
    var authenticationOptionsLoadedAt = 0;
    var PASSKEY_BUTTON_HTML =
        '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path fill="currentColor" d="M5 20q-.825 0-1.412-.587T3 18v-.8q0-.85.438-1.562T4.6 14.55q1.55-.775 3.15-1.162T11 13q.35 0 .7.013t.7.062q.275.025.437.213t.163.462q.05 1.175.575 2.213t1.4 1.762q.175.125.275.313t.1.412V19q0 .425-.288.713T14.35 20zm6-8q-1.65 0-2.825-1.175T7 8t1.175-2.825T11 4t2.825 1.175T15 8t-1.175 2.825T11 12m7.5 2q.425 0 .713-.288T19.5 13t-.288-.712T18.5 12t-.712.288T17.5 13t.288.713t.712.287m.15 8.65l-1-1q-.05-.05-.15-.35v-4.45q-1.1-.325-1.8-1.237T15 13.5q0-1.45 1.025-2.475T18.5 10t2.475 1.025T22 13.5q0 1.125-.638 2t-1.612 1.25l.9.9q.15.15.15.35t-.15.35l-.8.8q-.15.15-.15.35t.15.35l.8.8q-.15.15-.15.35t.15.35l.8.8q.15.15.15.35t-.15.35l-1.3 1.3q-.15.15-.35.15t-.35-.15"/></svg><span>使用通行密钥登录</span>';

    function b64e(buf) {
        var bytes = new Uint8Array(buf);
        var s = "";
        for (var i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
        return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
    }

    function b64d(str) {
        var b = str.replace(/-/g, "+").replace(/_/g, "/");
        b = b + "=".repeat((4 - (b.length % 4)) % 4);
        var bin = atob(b);
        var bytes = new Uint8Array(bin.length);
        for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        return bytes.buffer;
    }

    function loadAuthenticationOptions() {
        var now = Date.now();
        if (authenticationOptions && now - authenticationOptionsLoadedAt < 60000) {
            return Promise.resolve(authenticationOptions);
        }
        if (authenticationOptionsPromise) return authenticationOptionsPromise;

        var promise = fetch(API + "/authentication/options", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ origin: window.location.origin }),
            credentials: "include",
        }).then(function (response) {
            return response.json().catch(function () { return {}; }).then(function (body) {
                if (!response.ok) throw new Error(body.message || "无法获取认证选项");
                return body;
            });
        }).then(function (options) {
            authenticationOptions = options;
            authenticationOptionsLoadedAt = Date.now();
            return options;
        });
        authenticationOptionsPromise = promise.then(function (options) {
            authenticationOptionsPromise = null;
            return options;
        }, function (error) {
            authenticationOptionsPromise = null;
            throw error;
        });
        return authenticationOptionsPromise;
    }

    function init() {
        var loginForm = document.getElementById("login-form");
        if (!loginForm) return;

        var pageUrl = new URL(window.location.href);
        var params = new URLSearchParams(window.location.search);
        var rememberMe = document.getElementById("remember-me");
        if (rememberMe && params.has("remember-me")) {
            rememberMe.checked = params.get("remember-me") === "true";
        }

        function ensureMessage(id, className) {
            var message = document.getElementById(id);
            if (message) return message;
            message = document.createElement("div");
            message.id = id;
            message.className = className;
            message.setAttribute("role", "alert");
            message.style.display = "none";
            loginForm.insertBefore(message, loginForm.firstChild);
            return message;
        }

        function showError(message) {
            var error = ensureMessage("passkey-error", "alert alert-error");
            error.textContent = message;
            error.style.display = "block";
        }

        function showUnsupported() {
            var warning = ensureMessage("passkey-warn", "alert");
            warning.textContent = "您的浏览器不支持通行密钥";
            warning.style.display = "block";
        }

        function updateLinks() {
            document.querySelectorAll('.pill-items a[href]').forEach(function (link) {
                var target = new URL(link.href, pageUrl);
                if (target.origin !== pageUrl.origin || target.pathname !== pageUrl.pathname
                    || !target.searchParams.has("method")) return;

                var method = target.searchParams.get("method");
                target.search = window.location.search;
                target.searchParams.set("method", method);
                if (rememberMe) target.searchParams.set("remember-me", String(rememberMe.checked));
                link.href = target.pathname + target.search + target.hash;
            });
        }

        function setBusy(trigger, busy, originalHtml) {
            trigger.setAttribute("aria-busy", String(busy));
            trigger.setAttribute("aria-disabled", String(busy));
            if (trigger.tagName === "BUTTON") {
                trigger.disabled = busy;
                trigger.innerHTML = busy ? "<span>正在验证...</span>" : originalHtml;
            }
        }

        async function authenticate(trigger, event) {
            if (event) event.preventDefault();
            if (authenticating) return;
            if (typeof window.PublicKeyCredential === "undefined") {
                showUnsupported();
                return;
            }

            authenticating = true;
            var originalHtml = trigger.innerHTML;
            var error = document.getElementById("passkey-error");
            if (error) error.style.display = "none";
            setBusy(trigger, true, originalHtml);

            try {
                var opts = await loadAuthenticationOptions();
                var cred = await navigator.credentials.get({
                    publicKey: {
                        challenge: b64d(opts.challenge),
                        rpId: opts.rpId,
                        timeout: opts.timeout,
                        userVerification: opts.userVerification,
                        allowCredentials: opts.allowCredentials && opts.allowCredentials.length > 0
                            ? opts.allowCredentials.map(function (id) {
                                return { type: "public-key", id: b64d(id) };
                            }) : undefined,
                    },
                });
                if (!cred) throw new Error("认证已取消");

                var verifyUrl = new URL(API + "/authentication/verify", window.location.origin);
                verifyUrl.searchParams.set("remember-me", String(Boolean(rememberMe && rememberMe.checked)));
                var res2 = await fetch(verifyUrl.pathname + verifyUrl.search, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        origin: window.location.origin,
                        sessionId: opts.sessionId,
                        credentialId: b64e(cred.rawId),
                        authenticatorData: b64e(cred.response.authenticatorData),
                        clientDataJSON: b64e(cred.response.clientDataJSON),
                        signature: b64e(cred.response.signature),
                        userHandle: cred.response.userHandle ? b64e(cred.response.userHandle) : null,
                    }),
                    credentials: "include",
                });
                if (!res2.ok) {
                    var verifyError = await res2.json().catch(function () { return {}; });
                    throw new Error(verifyError.message || "认证失败");
                }

                window.location.href = new URLSearchParams(window.location.search).get("redirect_uri") || "/uc";
                authenticationOptions = null;
                authenticationOptionsLoadedAt = 0;
            } catch (error) {
                if (error.name === "NotAllowedError") showError("认证已取消或被拒绝");
                else if (error.name === "AbortError") showError("操作已取消");
                else if (error.name === "TimeoutError" || (error.message && error.message.includes("timed out")))
                    showError("操作超时，请重试");
                else showError(error.message || "认证失败");
                setBusy(trigger, false, originalHtml);
            } finally {
                authenticating = false;
            }
        }

        function findPasskeyLink(target) {
            while (target && target !== document) {
                if (target.nodeType === 1 && target.matches && target.matches('.pill-items a[href]')) {
                    var linkUrl = new URL(target.href, pageUrl);
                    if (linkUrl.origin === pageUrl.origin
                        && linkUrl.pathname === pageUrl.pathname
                        && linkUrl.searchParams.get("method") === "passkey") {
                        return target;
                    }
                    return null;
                }
                target = target.parentNode;
            }
            return null;
        }

        function handlePasskeyPointer(event) {
            updateLinks();
            var link = findPasskeyLink(event.target);
            if (!link || (event.button !== undefined && event.button !== 0) || event.metaKey || event.ctrlKey
                || event.shiftKey || event.altKey) return;
            event.preventDefault();
            event.stopImmediatePropagation();
            if (event.type === "click" || event.type === "pointerup" || !event.pointerType) {
                authenticate(link, event);
            }
        }

        function bindPasskeyButton() {
            if (params.get("method") !== "passkey") return;
            var button = loginForm.querySelector('button[type="submit"]');
            if (!button || button.dataset.passkeyBound) return;
            button.dataset.passkeyBound = "true";
            if (typeof window.PublicKeyCredential === "undefined") {
                showUnsupported();
                button.disabled = true;
                return;
            }
            button.innerHTML = PASSKEY_BUTTON_HTML;
            button.addEventListener("click", authenticate);
        }

        function preloadAuthenticationOptions() {
            if (typeof fetch !== "function" || typeof window.PublicKeyCredential === "undefined") return;
            var hasPasskeyLink = document.querySelector('.pill-items a[href*="method=passkey"]');
            if (params.get("method") === "passkey" || hasPasskeyLink) {
                loadAuthenticationOptions().catch(function () {
                    // The click handler retries after a failed prefetch and shows the error to the user.
                });
            }
        }

        updateLinks();
        bindPasskeyButton();
        preloadAuthenticationOptions();
        if (rememberMe) rememberMe.addEventListener("change", updateLinks);
        document.addEventListener("pointerup", handlePasskeyPointer, true);
        document.addEventListener("touchend", handlePasskeyPointer, true);
        document.addEventListener("click", handlePasskeyPointer, true);
        document.addEventListener("auxclick", updateLinks, true);
        window.addEventListener("pageshow", updateLinks);
    }

    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
    else init();
})();
