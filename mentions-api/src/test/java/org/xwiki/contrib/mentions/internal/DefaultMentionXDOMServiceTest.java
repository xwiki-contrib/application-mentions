package org.xwiki.contrib.mentions.internal;/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Named;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.NewLineBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import ch.qos.logback.classic.Level;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.xwiki.test.LogLevel.WARN;

/**
 * Test of {@link DefaultMentionXDOMService}.
 *
 * @version $Id$
 * @since 1.0
 */
@ComponentTest
public class DefaultMentionXDOMServiceTest
{
    @InjectMockComponents
    private DefaultMentionXDOMService xdomService;

    @RegisterExtension
    LogCaptureExtension logCapture = new LogCaptureExtension(WARN);

    @MockComponent
    @Named("xwiki/2.1")
    private Parser parser;

    @Test
    void listMentionMacros()
    {
        List<MacroBlock> actual = this.xdomService.listMentionMacros(new XDOM(singletonList(new ParagraphBlock(asList(
            new NewLineBlock(),
            new GroupBlock(singletonList(
                new MacroBlock("mention", new HashMap<>(), true)
            ))
        )))));
        assertEquals(1, actual.size());
        assertEquals(new MacroBlock("mention", new HashMap<>(), true), actual.get(0));
    }

    @Test
    void countByIdentifierEmpty()
    {
        Map<String, Long> actual = this.xdomService.countByIdentifier(emptyList());
        assertTrue(actual.isEmpty());
    }

    @Test
    void countByIdentifierOne()
    {
        Map<String, Long> actual = this.xdomService.countByIdentifier(singletonList(
            initMentionMacro("A")
        ));
        HashMap<String, Long> expected = new HashMap<>();
        expected.put("A", 1L);
        assertEquals(expected, actual);
    }

    @Test
    void countByIdentifierTwo()
    {
        Map<String, Long> actual = this.xdomService.countByIdentifier(asList(
            initMentionMacro("A"),
            initMentionMacro("A")
        ));
        HashMap<String, Long> expected = new HashMap<>();
        expected.put("A", 2L);
        assertEquals(expected, actual);
    }

    @Test
    void countByIdentifierThree()
    {
        Map<String, Long> actual = this.xdomService.countByIdentifier(asList(
            initMentionMacro("A"),
            initMentionMacro("B"),
            initMentionMacro("A")
        ));
        HashMap<String, Long> expected = new HashMap<>();
        expected.put("B", 1L);
        expected.put("A", 2L);
        assertEquals(expected, actual);
    }

    @Test
    void parse() throws Exception
    {
        XDOM xdom = new XDOM(emptyList());
        when(this.parser.parse(any(Reader.class))).thenReturn(xdom);

        Optional<XDOM> actual = this.xdomService.parse("ABC");

        assertEquals(Optional.of(xdom), actual);
    }

    @Test
    void parseError() throws Exception
    {
        when(this.parser.parse(any(Reader.class))).thenThrow(new ParseException(""));

        Optional<XDOM> actual = this.xdomService.parse("ABC");

        assertEquals(1, this.logCapture.size());
        assertEquals(Level.WARN, this.logCapture.getLogEvent(0).getLevel());
        assertEquals("Failed to parse the payload [ABC]. Cause [ParseException: ].", this.logCapture.getMessage(0));

        assertEquals(Optional.empty(), actual);
    }

    private MacroBlock initMentionMacro(String identifier)
    {
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put("identifier", identifier);
        return new MacroBlock("mention", parameters, false);
    }
}