package com.example.submission

/** What the portal page says after an entry code was submitted. */
enum class PortalResult { SUBMITTED, DUPLICATE, UNKNOWN }

object SubmissionAutomationHelper {

    /**
     * Safely unwraps a JSON string literal returned by `WebView.evaluateJavascript`,
     * returning `""` when the result is `null` or `"null"`.
     */
    fun unquoteJsString(rawJsResult: String?): String {
        val trimmed = rawJsResult?.trim() ?: return ""
        if (trimmed.isEmpty() || trimmed == "null") return ""
        return trimmed.removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    /**
     * Maps the raw value returned by `evaluateJavascript` for [generateCheckSuccessScript]
     * (a JSON string such as `"SUBMITTED"`, or `null`) to a [PortalResult].
     */
    fun parsePortalResult(rawJsResult: String?): PortalResult =
        when (unquoteJsString(rawJsResult)) {
            "SUBMITTED" -> PortalResult.SUBMITTED
            "DUPLICATE" -> PortalResult.DUPLICATE
            else -> PortalResult.UNKNOWN
        }

    fun generateLoginScript(email: String, pass: String): String {
        val safeEmail = escapeJsString(email)
        val safePass = escapeJsString(pass)
        return """
            (function() {
                try {
                    var emailInput = document.querySelector('input[type="email"]') ||
                                     document.querySelector('input[name*="email" i]') ||
                                     document.querySelector('input[name*="user" i]') ||
                                     document.querySelector('input[id*="email" i]') ||
                                     document.querySelector('input[id*="user" i]');
                                     
                    var passInput = document.querySelector('input[type="password"]') ||
                                    document.querySelector('input[name*="pass" i]') ||
                                    document.querySelector('input[id*="pass" i]');
                                    
                    if (emailInput) {
                        emailInput.value = '$safeEmail';
                        emailInput.dispatchEvent(new Event('input', { bubbles: true }));
                        emailInput.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                    
                    if (passInput) {
                        passInput.value = '$safePass';
                        passInput.dispatchEvent(new Event('input', { bubbles: true }));
                        passInput.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                    
                    if (emailInput && passInput) {
                        var submitBtn = document.querySelector('button[type="submit"]') ||
                                        document.querySelector('input[type="submit"]') ||
                                        document.querySelector('.btn-login') ||
                                        document.querySelector('#login-btn') ||
                                        document.querySelector('button.login');
                        if (submitBtn) {
                            setTimeout(function() { submitBtn.click(); }, 300);
                        }
                        return "SUCCESS: Credentials auto-filled";
                    } else {
                        return "PARTIAL: Email or password input field not detected on current page";
                    }
                } catch(e) {
                    return "ERROR: " + e.message;
                }
            })();
        """.trimIndent()
    }

    /**
     * Fills the 2nd Chance Zone "Enter Your Tickets" form: picks the ticket's game in the
     * "Select game" dropdown (options read "REINDEER RICHES - 7676"), types the entry code and
     * presses Submit Entry. The game is matched by its 4-digit number, then by title.
     *
     * Returns a status string:
     *  - `SUCCESS: ...` the game (if the page has a game list) and code were filled and submit pressed
     *  - `NO_GAME: ...` the page has a game list but this game isn't in it; nothing is submitted
     *  - `NO_FORM: ...` there is no entry form on this page (e.g. not on /enter, or logged out)
     *  - `ERROR: ...`
     */
    fun generateCodeSubmissionScript(entryCode: String, gameNumber: String, gameTitle: String = ""): String {
        val safeCode = escapeJsString(entryCode)
        val safeGame = escapeJsString(gameNumber)
        val safeTitle = escapeJsString(gameTitle)
        return """
            (function() {
                try {
                    var CODE = '$safeCode', GAME = '$safeGame', TITLE = '$safeTitle';
                    function norm(s) { return (s || '').toLowerCase().replace(/[^a-z0-9]/g, ''); }
                    function visible(el) {
                        return !!el && !el.disabled && el.type !== 'hidden' &&
                               !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
                    }
                    // Sets a value the way a user would, so React/Angular/Vue forms notice it.
                    function setValue(el, v) {
                        var proto = el.tagName === 'SELECT' ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
                        var desc = Object.getOwnPropertyDescriptor(proto, 'value');
                        if (desc && desc.set) { desc.set.call(el, v); } else { el.value = v; }
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        el.dispatchEvent(new Event('blur', { bubbles: true }));
                    }
                    function numbersIn(s) { return (s || '').replace(/[^0-9]/g, ' ').split(' '); }

                    // 1. The game dropdown: a <select> with an option carrying the game number.
                    var selects = Array.prototype.slice.call(document.querySelectorAll('select'));
                    var gameSelect = null, gameOption = null;
                    for (var i = 0; i < selects.length && !gameOption; i++) {
                        var opts = selects[i].options;
                        for (var j = 0; j < opts.length; j++) {
                            if (GAME && numbersIn(opts[j].text + ' ' + opts[j].value).indexOf(GAME) >= 0) {
                                gameSelect = selects[i]; gameOption = opts[j]; break;
                            }
                        }
                    }
                    var wantedTitle = norm(TITLE);
                    for (var i2 = 0; i2 < selects.length && !gameOption && wantedTitle.length >= 4; i2++) {
                        var opts2 = selects[i2].options;
                        for (var j2 = 0; j2 < opts2.length; j2++) {
                            var label = norm(opts2[j2].text).replace(/[0-9]{4}${'$'}/, '');
                            if (label && label === wantedTitle) { gameSelect = selects[i2]; gameOption = opts2[j2]; break; }
                        }
                    }
                    // A dropdown that offers games ("select game", or options ending in a game number).
                    var gameList = gameSelect || selects.filter(function(s) {
                        if (/game/i.test((s.name || '') + ' ' + (s.id || '') + ' ' + (s.options[0] ? s.options[0].text : ''))) return true;
                        return Array.prototype.some.call(s.options, function(o) { return /-\s*[0-9]{4}\s*${'$'}/.test(o.text); });
                    })[0] || null;

                    // 2. The entry code box.
                    var scope = (gameList && gameList.form) || document;
                    var codeInput = scope.querySelector('input[name*="code" i]') ||
                                    scope.querySelector('input[id*="code" i]') ||
                                    scope.querySelector('input[placeholder*="code" i]') ||
                                    scope.querySelector('input[name*="entry" i]') ||
                                    scope.querySelector('input[id*="entry" i]') ||
                                    scope.querySelector('input[name*="ticket" i]') ||
                                    scope.querySelector('input[aria-label*="code" i]');
                    if (!codeInput && gameList) {
                        // Entry code box with no telling name: the first text box in the form.
                        codeInput = Array.prototype.slice.call(scope.querySelectorAll('input')).filter(function(el) {
                            var t = (el.type || 'text').toLowerCase();
                            return (t === 'text' || t === 'tel' || t === 'number' || t === '') && visible(el);
                        })[0] || null;
                    }

                    if (!codeInput && !gameList) {
                        return "NO_FORM: No entry form on this page";
                    }

                    if (gameList && !gameOption) {
                        if (codeInput) { setValue(codeInput, CODE); }
                        return "NO_GAME: Game #" + GAME + " isn't in the Select game list. Pick it by hand.";
                    }

                    if (gameOption) {
                        gameSelect.focus();
                        gameOption.selected = true;
                        setValue(gameSelect, gameOption.value);
                    }

                    function fillCode() {
                        var box = codeInput || (gameList && gameList.form && gameList.form.querySelector('input[type="text"]'));
                        if (box) { box.focus(); setValue(box, CODE); }
                        return !!box;
                    }
                    var codeFilled = fillCode();
                    // Some forms re-render after the game changes; fill again once they settle.
                    setTimeout(fillCode, 300);

                    function findSubmit() {
                        var root = (gameList && gameList.form) || (codeInput && codeInput.form) || document;
                        var btn = root.querySelector('button[type="submit"]') ||
                                  root.querySelector('input[type="submit"]') ||
                                  root.querySelector('.submit-ticket') ||
                                  root.querySelector('#submit-ticket') ||
                                  root.querySelector('button.submit');
                        if (btn) return btn;
                        return Array.prototype.slice.call(document.querySelectorAll('button, input[type="button"], a'))
                            .filter(function(el) { return /submit\s*entry|^\s*submit\s*${'$'}/i.test(el.innerText || el.value || ''); })[0] || null;
                    }
                    var submitBtn = findSubmit();
                    var picked = gameOption ? ('Selected ' + gameOption.text.trim() + '. ') : '';
                    if (submitBtn) {
                        setTimeout(function() { (findSubmit() || submitBtn).click(); }, 700);
                        return "SUCCESS: " + picked + "Code filled and submit triggered";
                    }
                    return codeFilled ? ("SUCCESS: " + picked + "Code filled. Press Submit Entry.")
                                      : ("NO_FORM: " + picked + "Entry code box not found");
                } catch(e) {
                    return "ERROR: " + e.message;
                }
            })();
        """.trimIndent()
    }

    fun generateCheckSuccessScript(): String {
        return """
            (function() {
                var bodyText = document.body ? document.body.innerText.toLowerCase() : "";
                var isSuccess = bodyText.includes("entry received") || 
                                bodyText.includes("successfully entered") || 
                                bodyText.includes("entry confirmed") ||
                                bodyText.includes("thank you for your entry");
                                
                var isDuplicate = bodyText.includes("already been entered") || 
                                  bodyText.includes("duplicate entry");
                                  
                if (isSuccess) return "SUBMITTED";
                if (isDuplicate) return "DUPLICATE";
                return "PENDING";
            })();
        """.trimIndent()
    }

    private fun escapeJsString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
