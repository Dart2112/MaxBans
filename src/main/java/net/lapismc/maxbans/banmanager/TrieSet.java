package net.lapismc.maxbans.banmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrieSet {
    private TrieNode top;

    TrieSet() {
        super();
        this.top = new TrieNode();
    }

    private TrieNode getNode(final String s) {
        TrieNode node = this.top;
        for (int i = 0; i < s.length(); ++i) {
            final Character c = s.charAt(i);
            node = node.get(c);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    private Path nearestPath(final String s) {
        final List<Path> potential = new ArrayList<>();
        final List<Path> next = new ArrayList<>();
        final TrieNode node = this.getNode(s);
        if (node == null) {
            return null;
        }
        final Path top = new Path(node);
        potential.add(top);
        while (!potential.isEmpty()) {
            for (final Path p : potential) {
                if (p.getNode().isWord()) {
                    return p;
                }
                for (final Map.Entry<Character, TrieNode> entry : p.getNode().getChildMap().entrySet()) {
                    final Character c = entry.getKey();
                    final TrieNode child = entry.getValue();
                    final Path q = new Path(p, child, c);
                    next.add(q);
                }
            }
            potential.clear();
            potential.addAll(next);
            next.clear();
        }
        return null;
    }

    String nearestKey(final String s) {
        Path p = this.nearestPath(s);
        if (p == null) {
            return null;
        }
        final char[] key = new char[p.getDepth()];
        while (p.getChar() != null) {
            key[p.getDepth() - 1] = p.getChar();
            p = p.getPrevious();
        }
        return String.valueOf(s) + new String(key);
    }

    public boolean contains(final String s) {
        final TrieNode node = this.getNode(s);
        return node != null && node.isWord();
    }

    public boolean add(final String key) {
        final int length = key.length();
        if (length != 0) {
            TrieNode node = this.top;
            int i = 0;
            do {
                final Character c = key.charAt(i);
                final TrieNode parent = node;
                node = node.get(c);
                if (node == null) {
                    node = parent.put(c);
                }
            } while (++i < length);
            return node.setWord();
        }
        if (this.top.isWord()) {
            return true;
        }
        this.top.setWord();
        return false;
    }

    void clear() {
        this.top = new TrieNode();
    }

    private class Path {
        private Path previous;
        private TrieNode node;
        private Character c;
        private int depth;

        Path(final Path previous, final TrieNode node, final Character c) {
            super();
            if (node == null) {
                throw new NullPointerException("Null node given");
            }
            this.c = c;
            this.previous = previous;
            this.node = node;
            this.depth = previous.getDepth() + 1;
        }

        Path(final TrieNode node) {
            super();
            if (node == null) {
                throw new NullPointerException("Null path given");
            }
            this.node = node;
            this.depth = 0;
        }

        int getDepth() {
            return this.depth;
        }

        Path getPrevious() {
            return this.previous;
        }

        TrieNode getNode() {
            return this.node;
        }

        Character getChar() {
            return this.c;
        }
    }

    private class TrieNode {
        private boolean isWord;
        private HashMap<Character, TrieNode> children;

        TrieNode() {
            this(false);
        }

        /*
        public TrieNode(final TrieSet set) {
            this(set, false);
        }
        */
        TrieNode(final boolean isWord) {
            super();
            this.children = new HashMap<>(5);
            this.isWord = isWord;
        }

        boolean isWord() {
            return this.isWord;
        }

        public TrieNode get(final Character c) {
            return this.children.get(c);
        }

        public TrieNode put(final Character c) {
            final TrieNode node = new TrieNode(true);
            this.children.put(c, node);
            return node;
        }

        HashMap<Character, TrieNode> getChildMap() {
            return this.children;
        }

        boolean setWord() {
            final boolean old = this.isWord;
            this.isWord = true;
            return old;
        }
    }
}
