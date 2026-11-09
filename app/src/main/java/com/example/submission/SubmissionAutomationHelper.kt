package com.example.submission

object SubmissionAutomationHelper {

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

    fun generateCodeSubmissionScript(entryCode: String, gameNumber: String): String {
        val safeCode = escapeJsString(entryCode)
        val safeGame = escapeJsString(gameNumber)
        return """
            (function() {
                try {
                    // Try to find the entry code input box
                    var codeInput = document.querySelector('input[name*="code" i]') ||
                                    document.querySelector('input[id*="code" i]') ||
                                    document.querySelector('input[name*="entry" i]') ||
                                    document.querySelector('input[id*="entry" i]') ||
                                    document.querySelector('input[placeholder*="code" i]') ||
                                    document.querySelector('input[name*="ticket" i]');
                                    
                    // Also try to find game number input if separated
                    var gameInput = document.querySelector('input[name*="game" i]') ||
                                    document.querySelector('input[id*="game" i]');
                                    
                    if (gameInput && '$safeGame') {
                        gameInput.value = '$safeGame';
                        gameInput.dispatchEvent(new Event('input', { bubbles: true }));
                        gameInput.dispatchEvent(new Event('change', { bubbles: true }));
                    }

                    if (codeInput) {
                        codeInput.focus();
                        codeInput.value = '$safeCode';
                        codeInput.dispatchEvent(new Event('input', { bubbles: true }));
                        codeInput.dispatchEvent(new Event('change', { bubbles: true }));
                        
                        // Look for Submit button
                        var submitBtn = document.querySelector('button[type="submit"]') ||
                                        document.querySelector('input[type="submit"]') ||
                                        document.querySelector('.submit-ticket') ||
                                        document.querySelector('#submit-ticket') ||
                                        document.querySelector('button.submit');
                                        
                        if (submitBtn) {
                            setTimeout(function() { submitBtn.click(); }, 400);
                            return "SUCCESS: Code filled and submit triggered";
                        }
                        return "SUCCESS: Code injected into input field";
                    } else {
                        return "NO_FIELD: Could not locate 2nd chance entry field on this page";
                    }
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
