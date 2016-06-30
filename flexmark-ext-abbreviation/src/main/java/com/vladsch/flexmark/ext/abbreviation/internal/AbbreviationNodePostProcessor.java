package com.vladsch.flexmark.ext.abbreviation.internal;

import com.vladsch.flexmark.ext.abbreviation.Abbreviation;
import com.vladsch.flexmark.ext.abbreviation.AbbreviationBlock;
import com.vladsch.flexmark.ext.abbreviation.AbbreviationExtension;
import com.vladsch.flexmark.internal.util.BasedSequence;
import com.vladsch.flexmark.internal.util.Escaping;
import com.vladsch.flexmark.internal.util.NodeTracker;
import com.vladsch.flexmark.internal.util.ReplacedTextMapper;
import com.vladsch.flexmark.node.DoNotLinkify;
import com.vladsch.flexmark.node.Document;
import com.vladsch.flexmark.node.Node;
import com.vladsch.flexmark.node.Text;
import com.vladsch.flexmark.parser.block.NodePostProcessor;
import com.vladsch.flexmark.parser.block.NodePostProcessorFactory;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AbbreviationNodePostProcessor extends NodePostProcessor {
    private Pattern abbreviations = null;
    private HashMap<String, String> abbreviationMap = null;

    private AbbreviationNodePostProcessor(Document document) {
        AbbreviationRepository abbrRepository = document.get(AbbreviationExtension.ABBREVIATIONS);

        if (!abbrRepository.isEmpty()) {
            abbreviationMap = new HashMap<>();
            StringBuilder sb = new StringBuilder();
            for (String abbr : abbrRepository.keySet()) {
                AbbreviationBlock abbreviationBlock = abbrRepository.get(abbr);
                BasedSequence abbreviation = abbreviationBlock.getAbbreviation();
                if (!abbreviation.isEmpty()) {
                    abbreviationMap.put(abbr, abbreviation.toString());

                    if (sb.length() > 0) sb.append("|");

                    if (Character.isLetterOrDigit(abbr.charAt(0))) sb.append("\\b");
                    sb.append("\\Q").append(abbr).append("\\E");
                    if (Character.isLetterOrDigit(abbr.charAt(abbr.length() - 1))) sb.append("\\b");
                }
            }

            if (sb.length() > 0) abbreviations = Pattern.compile(sb.toString());
        }
    }

    @Override
    public void process(NodeTracker state, Node node) {
        if (abbreviations == null) return; 
        
        BasedSequence original = node.getChars();
        ReplacedTextMapper textMapper = new ReplacedTextMapper(original);
        BasedSequence literal = Escaping.unescape(original, textMapper);

        Matcher m = abbreviations.matcher(literal);
        Node lastNode = node;
        int lastEscaped = 0;

        while (m.find()) {
            //String found = m.group();
            if (abbreviationMap.containsKey(m.group(0))) {
                String abbreviation = abbreviationMap.get(m.group(0));

                BasedSequence abbrText = literal.subSequence(m.start(0), m.end(0));
                int startOffset = textMapper.originalOffset(m.start(0));
                int endOffset = textMapper.originalOffset(m.end(0));

                if (startOffset != lastEscaped) {
                    BasedSequence escapedChars = original.subSequence(lastEscaped, startOffset);
                    Node node1 = new Text(escapedChars);
                    lastNode.insertAfter(node1);
                    lastNode = node1;
                    state.nodeAdded(lastNode);
                }

                BasedSequence origAbbrText = original.subSequence(startOffset, endOffset);
                Abbreviation abbrNode = new Abbreviation(origAbbrText, abbreviation);
                lastNode.insertAfter(abbrNode);
                lastNode = abbrNode;
                state.nodeAdded(lastNode);

                lastEscaped = endOffset;
            }
        }

        if (lastEscaped != original.length()) {
            BasedSequence escapedChars = original.subSequence(lastEscaped, original.length());
            Node node1 = new Text(escapedChars);
            lastNode.insertAfter(node1);
            state.nodeAdded(lastNode);
        }
        
        lastNode = node.getNext();
        node.unlink();
        state.nodeRemoved(node);
    }

    public static class Factory extends NodePostProcessorFactory {
        public Factory() {
            super(false);
            
            addNodeWithExclusions(Text.class, DoNotLinkify.class);
        }

        @Override
        public NodePostProcessor create(Document document) {
            return new AbbreviationNodePostProcessor(document);
        }
    }
}
