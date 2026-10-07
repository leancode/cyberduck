package ch.cyberduck.ui.fx;

/*
 * Copyright (c) 2002-2026 iterate GmbH. All rights reserved.
 * https://cyberduck.io/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

import ch.cyberduck.core.LocaleFactory;

/**
 * Translated labels. The English text is the key, as in the translations shared with the other platforms. The
 * translations are spread over several tables there, so look in the ones that hold labels of windows and dialogs and
 * show the English text when none has it.
 */
public final class Messages {

    private static final String[] TABLES = {
        "Localizable", "Browser", "Folder", "Transfer", "Credentials", "Connection", "Bookmark", "Download", "Alert",
        "Prompt", "Main", "File", "Edit", "Login", "Status", "Preferences", "Error"
    };

    private Messages() {
        //
    }

    /**
     * @param key English text
     * @return Text in the language of the user or the English text
     */
    public static String get(final String key) {
        if(null == key || key.isEmpty()) {
            return key;
        }
        for(String table : TABLES) {
            final String localized = LocaleFactory.localizedString(key, table);
            if(!key.equals(localized)) {
                return localized;
            }
        }
        return key;
    }
}
