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

import ch.cyberduck.core.DefaultPathAttributes;
import ch.cyberduck.core.Path;
import ch.cyberduck.core.PathAttributes;
import ch.cyberduck.core.StaticPermission;

import org.junit.Before;
import org.junit.Test;

import java.util.EnumSet;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InfoControllerTest {

    @Before
    public void setup() {
        FxToolkit.init();
    }

    private static <T> T onFx(final Callable<T> callable) throws Exception {
        final FutureTask<T> task = new FutureTask<>(callable);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testFile() throws Exception {
        final PathAttributes attributes = new DefaultPathAttributes();
        attributes.setSize(2048L);
        attributes.setPermission(new StaticPermission(640));
        attributes.setOwner("alice");
        attributes.setModificationDate(1_700_000_000_000L);
        final InfoController info = new InfoController(null, null, null, new Path("/home/alice/a.txt", EnumSet.of(Path.Type.file)));
        onFx(() -> {
            info.update(attributes);
            return null;
        });
        assertEquals(2048L, info.getBytes());
        assertTrue(info.getSize().getText(), info.getSize().getText().contains("2,048 bytes"));
        assertEquals("rw-r----- (640)", info.getPermissions().getText());
    }

    @Test
    public void testFolderHasNoSize() throws Exception {
        final PathAttributes attributes = new DefaultPathAttributes();
        attributes.setSize(4096L);
        final InfoController info = new InfoController(null, null, null, new Path("/home/alice", EnumSet.of(Path.Type.directory)));
        onFx(() -> {
            info.update(attributes);
            return null;
        });
        assertEquals(-1L, info.getBytes());
        assertEquals("", info.getSize().getText());
    }

    @Test
    public void testPermissionBoxesFollowTheOctalNumberAndBack() throws Exception {
        final PathAttributes attributes = new DefaultPathAttributes();
        attributes.setPermission(new StaticPermission(640));
        final InfoController info = new InfoController(null, null, null, new Path("/home/alice/a.txt", EnumSet.of(Path.Type.file)));
        onFx(() -> {
            info.build();
            info.update(attributes);
            assertEquals("640", info.getOctal().getText());
            assertTrue(info.getBit(0, 0).isSelected() && info.getBit(0, 1).isSelected() && !info.getBit(0, 2).isSelected());
            assertTrue(info.getBit(1, 0).isSelected() && !info.getBit(1, 1).isSelected());
            assertFalse(info.getBit(2, 0).isSelected());

            info.getOctal().setText("755");
            assertTrue(info.getBit(1, 0).isSelected() && !info.getBit(1, 1).isSelected() && info.getBit(1, 2).isSelected());
            assertTrue(info.getBit(2, 0).isSelected() && info.getBit(2, 2).isSelected());

            // A box that is clicked changes the number
            // A disabled box ignores fire(), and without a protocol with permissions the editor is disabled
            info.getBit(2, 1).setSelected(true);
            info.getBit(2, 1).getOnAction().handle(new javafx.event.ActionEvent());
            assertEquals("757", info.getOctal().getText());
            assertEquals("rwxr-xrwx", info.entered().getSymbol());
            return null;
        });
    }

    @Test
    public void testNotValidOctalNumberIsNotApplied() throws Exception {
        final InfoController info = new InfoController(null, null, null, new Path("/a", EnumSet.of(Path.Type.file)));
        onFx(() -> {
            info.build();
            info.getOctal().setText("789");
            assertNull(info.entered());
            info.getOctal().setText("64");
            assertNull(info.entered());
            return null;
        });
    }

    @Test
    public void testNothingToChangeWithoutAProtocolThatHasPermissions() throws Exception {
        final InfoController info = new InfoController(null, null, null, new Path("/a", EnumSet.of(Path.Type.file)));
        onFx(() -> {
            info.build();
            assertTrue(info.getApply().isDisabled());
            return null;
        });
    }
}
