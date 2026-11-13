package com.example.submission

/** What the portal page says after an entry code was submitted. */
enum class PortalResult {
    /** The site confirmed the entry. */
    SUBMITTED,
    /** The site says this code was already entered. */
    DUPLICATE,
    /** The site showed an error (empty field, invalid code, wrong game...); nothing was entered. */
    REJECTED,
    /** Too many failed tries: the site blocks entries for several hours. Never submit while locked. */
    LOCKED,
    /** Nothing conclusive on the page yet. */
    UNKNOWN
}

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
        when (unquoteJsString(rawJsResult).substringBefore('|')) {
            "SUBMITTED" -> PortalResult.SUBMITTED
            "DUPLICATE" -> PortalResult.DUPLICATE
            "REJECTED" -> PortalResult.REJECTED
            "LOCKED" -> PortalResult.LOCKED
            else -> PortalResult.UNKNOWN
        }

    /**
     * True when the result check saw the user (or the app) press Submit on the page since the
     * last Auto-Fill, so a message on the page can be trusted as the answer to that attempt.
     */
    fun submitSeen(rawJsResult: String?): Boolean = unquoteJsString(rawJsResult).endsWith("|S")

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
     *  - `NO_CODE_BOX: ...` the game was picked but the Entry Code box wasn't found; nothing is submitted
     *  - `ERROR: ...`
     */
    /** Text the site shows after too many failed entry attempts (lowercase, matched in page text). */
    const val LOCKOUT_PHRASE = "exceeded the maximum number of unsuccessful"
    const val DEFAULT_LOCKOUT_HOURS = 8

    /**
     * Hours the site says to wait, read from "...You may try again in 8 hours.", or [default].
     */
    fun lockoutHours(pageText: String, default: Int = DEFAULT_LOCKOUT_HOURS): Int =
        Regex("""(?i)try again in\s+(\d{1,2})\s+hour""").find(pageText)?.groupValues?.get(1)?.toIntOrNull() ?: default

    /**
     * [autoSubmit] false fills the game and code and leaves pressing Submit Entry to the user.
     */
    fun generateCodeSubmissionScript(
        entryCode: String,
        gameNumber: String,
        gameTitle: String = "",
        autoSubmit: Boolean = false
    ): String {
        val safeCode = escapeJsString(entryCode)
        val safeGame = escapeJsString(gameNumber)
        val safeTitle = escapeJsString(gameTitle)
        return """
            (function() {
                try {
                    var CODE = '$safeCode', GAME = '$safeGame', TITLE = '$safeTitle', AUTO_SUBMIT = $autoSubmit;
                    // Locked out after too many failed tries: touch nothing.
                    var pageText = document.body ? (document.body.innerText || '').toLowerCase() : '';
                    if (pageText.indexOf('$LOCKOUT_PHRASE') >= 0) {
                        return "LOCKED: " + ((pageText.match(/try again in\s+\d+\s+hours?/) || ['try again later'])[0]);
                    }
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

                    // 2. The entry code box. Looked up fresh every time, because picking a game can
                    // make the page rebuild the form and replace the box. Hidden inputs whose
                    // names contain "code" (tokens, promo ids) are never used.
                    function textLike(el) {
                        var t = (el.getAttribute('type') || 'text').toLowerCase();
                        return (t === 'text' || t === 'tel' || t === 'number' || t === 'search') &&
                               !el.readOnly && visible(el);
                    }
                    function currentSelect() {
                        if (!gameSelect && !gameList) return null;
                        var all = Array.prototype.slice.call(document.querySelectorAll('select'));
                        var ref = gameSelect || gameList;
                        if (document.contains(ref)) return ref;
                        return all.filter(function(s) {
                            return Array.prototype.some.call(s.options, function(o) { return /-\s*[0-9]{4}\s*${'$'}/.test(o.text); });
                        })[0] || null;
                    }
                    function findCodeBox() {
                        var inputs = Array.prototype.slice.call(document.querySelectorAll('input')).filter(textLike);
                        // a) The box labelled "Entry Code".
                        var labels = Array.prototype.slice.call(document.querySelectorAll('label'));
                        for (var k = 0; k < labels.length; k++) {
                            if (!/entry\s*code/i.test(labels[k].innerText || labels[k].textContent || '')) continue;
                            var byFor = labels[k].htmlFor ? document.getElementById(labels[k].htmlFor) : null;
                            if (byFor && byFor.tagName === 'INPUT' && textLike(byFor)) return byFor;
                            var inside = labels[k].querySelector('input');
                            if (inside && textLike(inside)) return inside;
                            var after = inputs.filter(function(el) {
                                return labels[k].compareDocumentPosition(el) & Node.DOCUMENT_POSITION_FOLLOWING;
                            })[0];
                            if (after) return after;
                        }
                        // b) A visible box whose name, id, placeholder or label mentions the code.
                        var named = inputs.filter(function(el) {
                            return /code|entry/i.test([el.name, el.id, el.placeholder, el.getAttribute('aria-label')].join(' '));
                        })[0];
                        if (named) return named;
                        // c) The first visible text box after the game list.
                        var sel = currentSelect();
                        if (sel) {
                            var next = inputs.filter(function(el) {
                                return sel.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_FOLLOWING;
                            })[0];
                            if (next) return next;
                        }
                        return null;
                    }
                    // Types the code like a person: focus, key events and value per character, so
                    // forms that listen for keys (masks, validators) accept it.
                    function typeInto(box, text) {
                        box.focus();
                        box.dispatchEvent(new Event('focus', { bubbles: true }));
                        setValue(box, '');
                        var desc = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
                        for (var c = 0; c < text.length; c++) {
                            var ch = text.charAt(c);
                            var keyInit = { key: ch, char: ch, bubbles: true, cancelable: true };
                            box.dispatchEvent(new KeyboardEvent('keydown', keyInit));
                            box.dispatchEvent(new KeyboardEvent('keypress', keyInit));
                            var v = text.substring(0, c + 1);
                            if (desc && desc.set) { desc.set.call(box, v); } else { box.value = v; }
                            box.dispatchEvent(new InputEvent('input', { data: ch, inputType: 'insertText', bubbles: true }));
                            box.dispatchEvent(new KeyboardEvent('keyup', keyInit));
                        }
                        box.dispatchEvent(new Event('change', { bubbles: true }));
                        box.dispatchEvent(new Event('blur', { bubbles: true }));
                    }
                    function norm2(v) { return (v || '').replace(/[^A-Za-z0-9]/g, '').toUpperCase(); }
                    function fillCode() {
                        var box = findCodeBox();
                        if (!box) return false;
                        if (norm2(box.value) !== norm2(CODE)) typeInto(box, CODE);
                        return norm2(box.value) === norm2(CODE);
                    }

                    var codeInput = findCodeBox();
                    if (!codeInput && !gameList) {
                        return "NO_FORM: No entry form on this page";
                    }

                    if (gameList && !gameOption) {
                        fillCode();
                        return "NO_GAME: Game #" + GAME + " isn't in the Select game list. Pick it by hand.";
                    }

                    if (gameOption) {
                        gameSelect.focus();
                        gameOption.selected = true;
                        setValue(gameSelect, gameOption.value);
                    }

                    function findSubmit() {
                        var box = findCodeBox();
                        var root = (box && box.form) || (gameSelect && gameSelect.form) || document;
                        var btn = root.querySelector('button[type="submit"]') ||
                                  root.querySelector('input[type="submit"]') ||
                                  root.querySelector('.submit-ticket') ||
                                  root.querySelector('#submit-ticket') ||
                                  root.querySelector('button.submit');
                        if (btn) return btn;
                        return Array.prototype.slice.call(document.querySelectorAll('button, input[type="button"], a'))
                            .filter(function(el) { return /submit\s*entry|^\s*submit\s*${'$'}/i.test(el.innerText || el.value || ''); })[0] || null;
                    }

                    // Note when Submit is pressed after this fill, so the result check can tell a
                    // fresh answer from a message that was already on the page.
                    window.__scSubmittedAt = 0;
                    if (!window.__scHooked) {
                        window.__scHooked = true;
                        var markSubmit = function() { window.__scSubmittedAt = Date.now(); };
                        document.addEventListener('submit', markSubmit, true);
                        document.addEventListener('click', function(e) {
                            var el = e.target && e.target.closest ? e.target.closest('button, input[type="submit"], input[type="button"], a') : null;
                            if (el && /submit/i.test(el.innerText || el.value || '')) markSubmit();
                        }, true);
                    }
                    var codeFilled = fillCode();
                    var picked = gameOption ? ('Selected ' + gameOption.text.trim() + '. ') : '';
                    if (!AUTO_SUBMIT || !findSubmit()) {
                        // Keep the code in the box for ~3 s while the form settles after the game
                        // change (it may be rebuilt), without ever pressing Submit.
                        var refills = 0;
                        (function keepFilled() {
                            refills++;
                            fillCode();
                            if (refills < 10) setTimeout(keepFilled, 300);
                        })();
                        if (codeFilled) {
                            return "FILLED: " + picked + "Code filled. Check it, then press Submit Entry.";
                        }
                        return findCodeBox()
                            ? ("NOT_FILLED: " + picked + "The code didn't stay in the box. Paste it (it's copied) and check it before Submit Entry.")
                            : ("NO_CODE_BOX: " + picked + "Couldn't find the Entry Code box. Paste the code and press Submit Entry.");
                    }
                    if (!findCodeBox()) {
                        return "NO_CODE_BOX: " + picked + "Couldn't find the Entry Code box. Paste the code and press Submit Entry.";
                    }
                    // The page may rebuild the form after the game changes: refill until the
                    // code sticks, and only then press Submit, so a blank form is never sent.
                    var tries = 0;
                    (function submitWhenFilled() {
                        tries++;
                        if (fillCode()) {
                            // Check once more after the page settles; if the box was rebuilt in
                            // the meantime, go round again instead of giving up.
                            setTimeout(function() {
                                if (fillCode()) {
                                    var b = findSubmit(); if (b) b.click();
                                } else if (tries < 8) {
                                    submitWhenFilled();
                                }
                            }, 300);
                        } else if (tries < 8) {
                            setTimeout(submitWhenFilled, 300);
                        }
                    })();
                    return "SUCCESS: " + picked + "Code filled and submit triggered";
                } catch(e) {
                    return "ERROR: " + e.message;
                }
            })();
        """.trimIndent()
    }

    fun generateCheckSuccessScript(): String {
        return """
            (function() {
                // Only the visible page text counts; FAQ answers in collapsed sections are ignored.
                var bodyText = document.body ? (document.body.innerText || "").toLowerCase() : "";
                var isDuplicate = bodyText.includes("already been entered") ||
                                  bodyText.includes("already been submitted") ||
                                  bodyText.includes("duplicate entry");
                var isRejected = bodyText.includes("please completely fill out") ||
                                 bodyText.includes("invalid entry code") ||
                                 bodyText.includes("entry code is invalid") ||
                                 bodyText.includes("not a valid") ||
                                 bodyText.includes("is not eligible") ||
                                 bodyText.includes("could not be processed") ||
                                 bodyText.includes("daily entry limit");
                var isSuccess = bodyText.includes("entry received") ||
                                bodyText.includes("successfully entered") ||
                                bodyText.includes("successfully submitted") ||
                                bodyText.includes("entry confirmed") ||
                                bodyText.includes("entry has been accepted") ||
                                bodyText.includes("thank you for your entry");

                // "|S": Submit was pressed since the last Auto-Fill (and the page had a moment to answer).
                var seen = (window.__scSubmittedAt && Date.now() - window.__scSubmittedAt > 1500) ? "|S" : "";
                if (bodyText.includes("$LOCKOUT_PHRASE")) return "LOCKED" + seen;
                if (isDuplicate) return "DUPLICATE" + seen;
                // An error on the page always wins over a success phrase elsewhere on it.
                if (isRejected) return "REJECTED" + seen;
                if (isSuccess) return "SUBMITTED" + seen;
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
