package org.xwiki.contrib.mentions.internal.async.jobs;/*
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.contrib.mentions.MentionIdentityService;
import org.xwiki.contrib.mentions.MentionXDOMService;
import org.xwiki.contrib.mentions.events.MentionEvent;
import org.xwiki.contrib.mentions.events.MentionEventParams;
import org.xwiki.contrib.mentions.internal.async.MentionsUpdatedRequest;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.ObservationManager;
import org.xwiki.rendering.block.IdBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.LargeStringProperty;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test of {@link MentionsUpdateJob}.
 *
 * @version $Id$
 * @since 1.0
 */
@ComponentTest
class MentionsUpdateJobTest
{
    @Mock
    private XWikiDocument newDocument;

    @Mock
    private XWikiDocument oldDocument;

    @Mock
    private XWikiContext context;

    @MockComponent
    protected ObservationManager observationManager;

    @MockComponent
    private MentionXDOMService xdomService;

    @MockComponent
    private MentionIdentityService identityService;

    @InjectMockComponents
    private MentionsUpdateJob job;

    @Test
    void runInternalNewMention()
    {
        XDOM dom1Mention = new XDOM(singletonList(new IdBlock("ID DOM1")));
        XDOM dom2Mentions = new XDOM(singletonList(new IdBlock("ID DOM2")));
        DocumentReference authorReference = new DocumentReference("xwiki", "XWiki", "Creator");
        DocumentReference documentReference = new DocumentReference("xwiki", "XWiki", "Doc");

        when(this.context.getDoc()).thenReturn(this.oldDocument);
        when(this.oldDocument.getXDOM()).thenReturn(dom1Mention);
        when(this.newDocument.getXDOM()).thenReturn(dom2Mentions);

        when(this.newDocument.getAuthorReference()).thenReturn(authorReference);
        when(this.newDocument.getDocumentReference()).thenReturn(documentReference);

        List<MacroBlock> l1mention = singletonList(
            new MacroBlock("mention", new HashMap<>(), false)
        );
        when(this.xdomService.listMentionMacros(dom1Mention)).thenReturn(l1mention);

        List<MacroBlock> l2mentions = asList(
            new MacroBlock("mention", new HashMap<>(), false),
            new MacroBlock("mention", new HashMap<>(), false)
        );
        when(this.xdomService.listMentionMacros(dom2Mentions)).thenReturn(l2mentions);

        Map<String, Long> value = new HashMap<>();
        value.put("u1", 1L);
        when(this.xdomService.countByIdentifier(l1mention)).thenReturn(value);
        Map<String, Long> value1 = new HashMap<>();
        value1.put("u1", 2L);
        when(this.xdomService.countByIdentifier(l2mentions)).thenReturn(value1);

        Set<String> value2 = new HashSet<>();
        value2.add("xwiki:XWiki.U1");
        when(this.identityService.resolveIdentity("u1")).thenReturn(value2);

        this.job.initialize(new MentionsUpdatedRequest(this.newDocument, this.oldDocument, authorReference));
        this.job.runInternal();

        MentionEventParams params = new MentionEventParams()
                                        .setUserReference(authorReference.toString())
                                        .setDocumentReference(documentReference.toString());
        MentionEvent event = new MentionEvent(value2, params);
        verify(this.observationManager)
            .notify(event, "org.xwiki.contrib:mentions-notifications", MentionEvent.EVENT_TYPE);
    }

    @Test
    void runInternalNewComment()
    {
        DocumentReference authorReference = new DocumentReference("xwiki", "XWiki", "Creator");
        DocumentReference documentReference = new DocumentReference("xwiki", "XWiki", "Doc");

        Map<DocumentReference, List<BaseObject>> value = new HashMap<>();
        BaseObject newComment = mock(BaseObject.class);
        when(newComment.getXClassReference()).thenReturn(new DocumentReference("xwiki", "XWiki", "XWikiComments"));
        LargeStringProperty newCommentLSP = new LargeStringProperty();
        newCommentLSP.setValue("COMMENT 1 CONTENT");
        when(newComment.getField("comment")).thenReturn(newCommentLSP);
        value.put(new DocumentReference("xwiki", "XWiki", "NewComment"), singletonList(newComment));
        when(this.newDocument.getXObjects()).thenReturn(value);
        when(this.newDocument.getDocumentReference()).thenReturn(documentReference);

        XDOM newCommentXDOM = new XDOM(emptyList());
        when(this.xdomService.parse("COMMENT 1 CONTENT")).thenReturn(Optional.of(newCommentXDOM));
        Map<String, String> parameters = new HashMap<>();
        parameters.put("identifier", "XWiki.U1");
        List<MacroBlock> newCommentNewMentions = singletonList(new MacroBlock("comment", parameters, false));
        when(this.xdomService.listMentionMacros(newCommentXDOM)).thenReturn(newCommentNewMentions);

        Map<String, Long> value1 = new HashMap<>();
        value1.put("XWiki.U1", 1L);
        when(this.xdomService.countByIdentifier(newCommentNewMentions)).thenReturn(value1);
        Set<String> value2 = new HashSet<>();
        value2.add("xwiki:XWiki.U1");
        when(this.identityService.resolveIdentity("XWiki.U1")).thenReturn(value2);

        this.job.initialize(new MentionsUpdatedRequest(this.newDocument, this.oldDocument, authorReference));
        this.job.runInternal();

        Set<String> targets = new HashSet<>();
        targets.add("xwiki:XWiki.U1");
        verify(this.observationManager)
            .notify(new MentionEvent(targets, new MentionEventParams()
                                                  .setUserReference(authorReference.toString())
                                                  .setDocumentReference(documentReference.toString())),
                "org.xwiki.contrib:mentions-notifications",
                MentionEvent.EVENT_TYPE);
    }

    @Test
    void runInternalEditComment()
    {
        DocumentReference authorReference = new DocumentReference("xwiki", "XWiki", "Creator");
        DocumentReference documentReference = new DocumentReference("xwiki", "XWiki", "Doc");
        DocumentReference commentDocRef = new DocumentReference("xwiki", "XWiki", "TheComment");

        Map<DocumentReference, List<BaseObject>> value = new HashMap<>();
        BaseObject newComment = mock(BaseObject.class);
        when(newComment.getXClassReference()).thenReturn(new DocumentReference("xwiki", "XWiki", "XWikiComments"));
        LargeStringProperty newCommentLSP = new LargeStringProperty();
        newCommentLSP.setValue("COMMENT 1 CONTENT");
        newCommentLSP.setName("comment");
        when(newComment.getField("comment")).thenReturn(newCommentLSP);
        value.put(commentDocRef, singletonList(newComment));
        when(this.newDocument.getXObjects()).thenReturn(value);
        when(this.newDocument.getDocumentReference()).thenReturn(documentReference);

        Map<DocumentReference, List<BaseObject>> value3 = new HashMap<>();
        BaseObject oldComment = mock(BaseObject.class);
        LargeStringProperty oldCommentLSP = new LargeStringProperty();
        oldCommentLSP.setValue("COMMENT 0 CONTENT");
        when(oldComment.getField("comment")).thenReturn(oldCommentLSP);
        value3.put(commentDocRef, singletonList(oldComment));
        when(this.oldDocument.getXObjects()).thenReturn(value3);

        XDOM newCommentXDOM = new XDOM(singletonList(new MacroBlock("mention", new HashMap<>(), false)));
        XDOM oldCommentXDOM = new XDOM(emptyList());
        when(this.xdomService.parse("COMMENT 1 CONTENT")).thenReturn(Optional.of(newCommentXDOM));
        when(this.xdomService.parse("COMMENT 0 CONTENT")).thenReturn(Optional.of(oldCommentXDOM));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("identifier", "XWiki.U1");
        List<MacroBlock> newCommentNewMentions = singletonList(new MacroBlock("comment", parameters, false));
        when(this.xdomService.listMentionMacros(newCommentXDOM)).thenReturn(newCommentNewMentions);

        Map<String, Long> value1 = new HashMap<>();
        value1.put("XWiki.U1", 1L);
        when(this.xdomService.countByIdentifier(newCommentNewMentions)).thenReturn(value1);
        Set<String> value2 = new HashSet<>();
        value2.add("xwiki:XWiki.U1");
        when(this.identityService.resolveIdentity("XWiki.U1")).thenReturn(value2);

        this.job.initialize(new MentionsUpdatedRequest(this.newDocument, this.oldDocument, authorReference));
        this.job.runInternal();

        Set<String> targets = new HashSet<>();
        targets.add("xwiki:XWiki.U1");
        verify(this.observationManager)
            .notify(new MentionEvent(targets, new MentionEventParams()
                                                  .setUserReference(authorReference.toString())
                                                  .setDocumentReference(documentReference.toString())),
                "org.xwiki.contrib:mentions-notifications",
                MentionEvent.EVENT_TYPE);
    }

    @Test
    void getType()
    {
        assertEquals("mentions-update-job", this.job.getType());
    }
}