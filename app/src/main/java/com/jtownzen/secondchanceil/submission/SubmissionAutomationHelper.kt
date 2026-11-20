package com.jtownzen.secondchanceil.submission

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

/**
 * What the result check saw on the page, gathered by [SubmissionAutomationHelper.generateCheckSuccessScript]
 * and decided in Kotlin by [SubmissionAutomationHelper.classify] so the rules can be unit-tested.
 */
data class PortalSnapshot(
    /** The lockout sentence is visible anywhere on the page. */
    val lockout: Boolean,
    /** Submit was pressed since the last Auto-Fill, at least 1.5 s ago. */
    val submitSeen: Boolean,
    /** `#entry-alert` is on screen with text: the site's own answer to the last entry. */
    val entryAlertVisible: Boolean,
    val entryAlertClass: String,
    val entryAlertText: String,
    /** Other visible alert-like elements (class to text), used only when `#entry-alert` is absent. */
    val otherAlerts: List<Pair<String, String>>
)

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
     * Maps the raw value returned by `evaluateJavascript` for [generateCheckSuccessScript] to a
     * [PortalResult]. Also accepts the older plain answers (`"SUBMITTED"`, `"REJECTED|S"`...).
     */
    fun parsePortalResult(rawJsResult: String?): PortalResult {
        parsePortalSnapshot(rawJsResult)?.let { return classify(it) }
        return when (unquoteJsString(rawJsResult).substringBefore('|')) {
            "SUBMITTED" -> PortalResult.SUBMITTED
            "DUPLICATE" -> PortalResult.DUPLICATE
            "REJECTED" -> PortalResult.REJECTED
            "LOCKED" -> PortalResult.LOCKED
            else -> PortalResult.UNKNOWN
        }
    }

    /**
     * True when the result check saw the user (or the app) press Submit on the page since the
     * last Auto-Fill, so a message on the page can be trusted as the answer to that attempt.
     */
    fun submitSeen(rawJsResult: String?): Boolean =
        parsePortalSnapshot(rawJsResult)?.submitSeen ?: unquoteJsString(rawJsResult).endsWith("|S")

    /** The site's own words for the last answer (the `#entry-alert` text), or "" when there is none. */
    fun portalMessage(rawJsResult: String?): String {
        val snap = parsePortalSnapshot(rawJsResult) ?: return ""
        if (snap.entryAlertVisible) return snap.entryAlertText
        return snap.otherAlerts.firstOrNull { classifyAnswer(it.second, it.first, trustClass = false) != PortalResult.UNKNOWN }
            ?.second.orEmpty()
    }

    /** Reads the `SC1|...` snapshot produced by [generateCheckSuccessScript], or null for anything else. */
    fun parsePortalSnapshot(rawJsResult: String?): PortalSnapshot? {
        val s = unquoteJsString(rawJsResult)
        if (!s.startsWith("SC1|")) return null
        val f = s.split('|')
        if (f.size < 7) return null
        fun dec(x: String): String = try {
            java.net.URLDecoder.decode(x, "UTF-8")
        } catch (_: Exception) {
            ""
        }
        val others = if (f[6].isEmpty()) emptyList() else f[6].split(',').mapNotNull { pair ->
            val parts = pair.split(':')
            if (parts.size == 2) dec(parts[0]) to dec(parts[1]) else null
        }
        return PortalSnapshot(
            lockout = f[1] == "1",
            submitSeen = f[2] == "1",
            entryAlertVisible = f[3] == "1",
            entryAlertClass = dec(f[4]),
            entryAlertText = dec(f[5]),
            otherAlerts = others
        )
    }

    private val DUPLICATE_RE = Regex("""already been (entered|submitted|used|registered)|duplicate""")
    private val SUCCESS_PHRASES = listOf(
        "has been accepted", "entry received", "successfully entered", "successfully submitted",
        "entry confirmed", "thank you for your entry"
    )
    /** Words that turn a success phrase around ("has not been accepted", "unable to..."). */
    private val NEGATION_RE = Regex("""\b(not|unable|failed|error|invalid|cannot|can't)\b""")
    private val REJECT_PHRASES = listOf(
        "please completely fill out", "ticket not entered", "invalid entry code", "entry code is invalid",
        "not a valid", "is not eligible", "could not be processed", "daily entry limit", "please try again",
        "not been accepted", "not accepted"
    )
    private val ERROR_CLASS_RE = Regex("""alert-danger|alert-warning|alert-error|\berror\b|\binvalid\b""")
    private val SUCCESS_CLASS_RE = Regex("""alert-success|\bsuccess\b""")

    /**
     * Decides one message. [trustClass] is true only for `#entry-alert`, the box the site answers
     * in: its red/green styling is part of the answer. Any other element needs matching words,
     * because sites style banners (maintenance notices, rules) the same way.
     */
    fun classifyAnswer(text: String, cssClass: String, trustClass: Boolean): PortalResult {
        val t = text.lowercase().trim()
        if (t.isEmpty()) return PortalResult.UNKNOWN
        val c = cssClass.lowercase()
        if (t.contains(LOCKOUT_PHRASE)) return PortalResult.LOCKED
        if (DUPLICATE_RE.containsMatchIn(t)) return PortalResult.DUPLICATE
        val negated = NEGATION_RE.containsMatchIn(t)
        if (SUCCESS_PHRASES.any { t.contains(it) } && !negated) return PortalResult.SUBMITTED
        if (trustClass && ERROR_CLASS_RE.containsMatchIn(c)) return PortalResult.REJECTED
        if (REJECT_PHRASES.any { t.contains(it) }) return PortalResult.REJECTED
        // Green styling never outweighs words that say otherwise ("has not been accepted").
        if (trustClass && !negated && SUCCESS_CLASS_RE.containsMatchIn(c)) return PortalResult.SUBMITTED
        return PortalResult.UNKNOWN
    }

    /**
     * The answer to the last entry. `#entry-alert` is authoritative when it is showing: the rest of
     * the page is instructions and rules, which mention "not eligible", "daily entry limit" and the
     * like, and must never overrule the site's actual answer. Without it, only visible alert-like
     * elements are read, and conflicting ones give [PortalResult.UNKNOWN] rather than a guess.
     */
    fun classify(snapshot: PortalSnapshot): PortalResult {
        if (snapshot.lockout) return PortalResult.LOCKED
        if (snapshot.entryAlertVisible) {
            return classifyAnswer(snapshot.entryAlertText, snapshot.entryAlertClass, trustClass = true)
        }
        val found = snapshot.otherAlerts
            .map { classifyAnswer(it.second, it.first, trustClass = false) }
            .filter { it != PortalResult.UNKNOWN }
            .toSet()
        return when {
            PortalResult.LOCKED in found -> PortalResult.LOCKED
            found.size == 1 -> found.single()
            else -> PortalResult.UNKNOWN
        }
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
                                    
                    function setInputValue(el, v) {
                        var desc = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
                        if (desc && desc.set) { desc.set.call(el, v); } else { el.value = v; }
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                    }

                    if (emailInput) {
                        setInputValue(emailInput, '$safeEmail');
                    }
                    
                    if (passInput) {
                        setInputValue(passInput, '$safePass');
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

    /** Elements that can carry a message other than `#entry-alert` (read only as a fallback). */
    private const val ALERT_SELECTOR = "[role=\"alert\"], .alert, .error-message, .invalid-feedback, .toast"
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
                    // The live form's game list is select#promotion_game_id with game numbers as values.
                    var exactSelect = document.getElementById('promotion_game_id');
                    if (exactSelect) selects = [exactSelect].concat(selects.filter(function(x) { return x !== exactSelect; }));
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
                        // The live 2nd Chance Zone form (checked 2026-11-13) names the box ticket1_1_1.
                        var exact = document.getElementById('ticket1_1_1');
                        if (exact && textLike(exact)) return exact;
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
                    // Messages already on the page before this attempt (rules, notices, an older
                    // answer) are never read as its answer by the fallback in the result check.
                    window.__scAlertBaseline = (function() {
                        var seenText = {};
                        var nodes = document.querySelectorAll('$ALERT_SELECTOR');
                        for (var b = 0; b < nodes.length; b++) {
                            var bt = (nodes[b].innerText || nodes[b].textContent || '').replace(/\s+/g, ' ').trim().slice(0, 400);
                            if (bt) seenText[bt] = 1;
                        }
                        return seenText;
                    })();
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

    /**
     * Reads what the page is showing after an entry, without deciding anything: whether the
     * lockout sentence is visible, whether Submit was pressed since the last Auto-Fill, the
     * `#entry-alert` box (class and text) and any other visible alert-like elements. Fields are
     * URI-encoded and joined with `|` as `SC1|lock|seen|alertVisible|alertClass|alertText|others`.
     * [classify] makes the decision.
     */
    fun generateCheckSuccessScript(): String {
        return """
            (function() {
                function vis(el) { return !!el && !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length); }
                function clean(t) { return (t || '').replace(/\s+/g, ' ').trim().slice(0, 400); }
                function enc(s) { return encodeURIComponent(s || ''); }
                function cls(el) { return (el.getAttribute && el.getAttribute('class')) || ''; }
                var body = document.body ? (document.body.innerText || '').toLowerCase() : '';
                var lock = body.indexOf('$LOCKOUT_PHRASE') >= 0 ? '1' : '0';
                // Submit was pressed since the last Auto-Fill (and the page had a moment to answer).
                var seen = (window.__scSubmittedAt && Date.now() - window.__scSubmittedAt > 1500) ? '1' : '0';
                // The live site answers every entry in #entry-alert.
                var a = document.getElementById('entry-alert');
                var aText = vis(a) ? clean(a.innerText || a.textContent) : '';
                // Fallback only: alerts that appeared after Auto-Fill. With no baseline (no fill
                // this page) nothing is read, so page copy can never pass for an answer.
                var others = [];
                var base = window.__scAlertBaseline;
                var nodes = base ? document.querySelectorAll('$ALERT_SELECTOR') : [];
                for (var i = 0; i < nodes.length && others.length < 6; i++) {
                    var n = nodes[i];
                    if (a && (n === a || a.contains(n) || n.contains(a))) continue;
                    if (!vis(n)) continue;
                    var t = clean(n.innerText || n.textContent);
                    if (t && !base[t]) others.push(enc(cls(n)) + ':' + enc(t));
                }
                return ['SC1', lock, seen, aText ? '1' : '0', enc(a ? cls(a) : ''), enc(aText), others.join(',')].join('|');
            })();
        """.trimIndent()
    }

    /**
     * Reads the Enter Your Tickets "Select game" list without touching it: every option's value and
     * text, URI-encoded, as `GL1|exact|value:text,...`. `exact` is 1 when the list came from the
     * form's own `select#promotion_game_id`. `GL1|0|` means there is no game list on the page.
     */
    fun generateReadGameListScript(): String {
        return """
            (function() {
                function enc(s) { return encodeURIComponent(s || ''); }
                var exact = document.getElementById('promotion_game_id');
                var sel = exact;
                if (!sel) {
                    var all = document.querySelectorAll('select');
                    for (var i = 0; i < all.length && !sel; i++) {
                        if (Array.prototype.some.call(all[i].options, function(o) { return /-\s*[0-9]{4}\s*${'$'}/.test(o.text); })) sel = all[i];
                    }
                }
                if (!sel) return 'GL1|0|';
                var out = [];
                for (var j = 0; j < sel.options.length; j++) {
                    var o = sel.options[j];
                    out.push(enc(o.value) + ':' + enc((o.text || '').replace(/\s+/g, ' ').trim()));
                }
                return 'GL1|' + (exact ? '1' : '0') + '|' + out.join(',');
            })();
        """.trimIndent()
    }

    /** The game list from [generateReadGameListScript], or null when the page has none (yet). */
    fun parsePortalGameList(rawJsResult: String?): com.jtownzen.secondchanceil.data.PortalGameList? {
        val s = unquoteJsString(rawJsResult)
        if (!s.startsWith("GL1|")) return null
        val f = s.split('|', limit = 3)
        if (f.size < 3 || f[2].isEmpty()) return null
        fun dec(x: String): String = try {
            java.net.URLDecoder.decode(x, "UTF-8")
        } catch (_: Exception) {
            ""
        }
        val options = f[2].split(',').mapNotNull { pair ->
            val parts = pair.split(':')
            if (parts.size != 2) return@mapNotNull null
            com.jtownzen.secondchanceil.data.GameLearner.parsePortalOption(dec(parts[0]), dec(parts[1]))
        }
        if (options.isEmpty()) return null
        return com.jtownzen.secondchanceil.data.PortalGameList(options, authoritative = f[1] == "1")
    }

    private fun escapeJsString(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")
            .replace("<", "\\u003C")
            .replace(">", "\\u003E")
    }
}
