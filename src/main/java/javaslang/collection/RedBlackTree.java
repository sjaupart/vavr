/*     / \____  _    ______   _____ / \____   ____  _____
 *    /  \__  \/ \  / \__  \ /  __//  \__  \ /    \/ __  \   Javaslang
 *  _/  // _\  \  \/  / _\  \\_  \/  // _\  \  /\  \__/  /   Copyright 2014-2015 Daniel Dietrich
 * /___/ \_____/\____/\_____/____/\___\_____/_/  \_/____/    Licensed under the Apache License, Version 2.0
 */
package javaslang.collection;

import javaslang.Lazy;
import javaslang.Tuple;
import javaslang.Tuple2;
import javaslang.Tuple3;
import javaslang.collection.Iterator.AbstractIterator;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Objects;

import static javaslang.collection.RedBlackTree.Color.BLACK;
import static javaslang.collection.RedBlackTree.Color.RED;

/**
 * Purely functional Red/Black Tree, inspired by <a href="https://github.com/kazu-yamamoto/llrbtree/blob/master/Data/Set/RBTree.hs">Kazu Yamamoto's Haskell implementation</a>.
 * <p>
 * Based on
 * <ul>
 * <li><a href="http://www.eecs.usma.edu/webs/people/okasaki/pubs.html#jfp99">Chris Okasaki, "Red-Black Trees in a Functional Setting", Journal of Functional Programming, 9(4), pp 471-477, July 1999</a></li>
 * <li>Stefan Kahrs, "Red-black trees with types", Journal of functional programming, 11(04), pp 425-432, July 2001</li>
 * </ul>
 *
 * @param <T> Component type
 */
public interface RedBlackTree<T> extends Iterable<T> {

    static <T extends Comparable<T>> Empty<T> empty() {
        return new Empty<>(T::compareTo);
    }

    static <T> Empty<T> empty(Comparator<? super T> comparator) {
        Objects.requireNonNull(comparator, "comparator is null");
        return new Empty<>(comparator);
    }

    static <T extends Comparable<T>> Node<T> of(T value) {
        final Empty<T> empty = empty();
        return new Node<>(BLACK, 1, empty, value, empty, empty);
    }

    static <T> Node<T> of(T value, Comparator<? super T> comparator) {
        Objects.requireNonNull(comparator, "comparator is null");
        final Empty<T> empty = empty(comparator);
        return new Node<>(BLACK, 1, empty, value, empty, empty);
    }

    default Node<T> add(T value) {
        return Node.insert(this, value).color(BLACK);
    }

    /**
     * Clears this RedBlackTree.
     *
     * @return An empty ReadBlackTree
     */
    Empty<T> clear();

    /**
     * Returns the underlying {@link java.util.Comparator} of this RedBlackTree.
     *
     * @return The comparator.
     */
    Comparator<? super T> comparator();

    /**
     * Checks, if this {@code RedBlackTree} contains the given {@code value}.
     *
     * @param value A value.
     * @return true, if this tree contains the value, false otherwise.
     */
    boolean contains(T value);

    /**
     * Deletes a value from this RedBlackTree.
     *
     * @param value A value
     * @return A new RedBlackTree if the value is present, otherwise this.
     */
    default RedBlackTree<T> delete(T value) {
        final RedBlackTree<T> tree = Node.delete(this, value)._1;
        return Node.color(tree, BLACK);
    }

    default RedBlackTree<T> difference(RedBlackTree<T> tree) {
        Objects.requireNonNull(tree, "tree is null");
        if (isEmpty() || tree.isEmpty()) {
            return this;
        } else {
            final Node<T> that = (Node<T>) tree;
            final Tuple2<RedBlackTree<T>, RedBlackTree<T>> split = Node.split(this, that.value);
            return Node.merge(split._1.difference(that.left), split._2.difference(that.right));
        }
    }

    default RedBlackTree<T> intersection(RedBlackTree<T> tree) {
        Objects.requireNonNull(tree, "tree is null");
        if (isEmpty()) {
            return this;
        } else if (tree.isEmpty()) {
            return tree;
        } else {
            final Node<T> that = (Node<T>) tree;
            final Tuple2<RedBlackTree<T>, RedBlackTree<T>> split = Node.split(this, that.value);
            if (contains(that.value)) {
                return Node.join(split._1.intersection(that.left), that.value, split._2.intersection(that.right));
            } else {
                return Node.merge(split._1.intersection(that.left), split._2.intersection(that.right));
            }
        }
    }

    /**
     * Checks if this {@code RedBlackTree} is empty, i.e. an instance of {@code Leaf}.
     *
     * @return true, if it is empty, false otherwise.
     */
    boolean isEmpty();

    default RedBlackTree<T> union(RedBlackTree<T> tree) {
        Objects.requireNonNull(tree, "tree is null");
        if (tree.isEmpty()) {
            return this;
        } else {
            final Node<T> that = (Node<T>) tree;
            if (isEmpty()) {
                return that.color(BLACK);
            } else {
                final Tuple2<RedBlackTree<T>, RedBlackTree<T>> split = Node.split(this, that.value);
                return Node.join(split._1.union(that.left), that.value, split._2.union(that.right));
            }
        }
    }

    /**
     * Returns an Iterator that iterates elements in the order induced by the underlying Comparator.
     * <p>
     * Internally an in-order traversal of the RedBlackTree is performed.
     * <p>
     * Example:
     *
     * <pre><code>
     *       4
     *      / \
     *     2   6
     *    / \ / \
     *   1  3 5  7
     * </code></post>
     *
     * Iteration order: 1, 2, 3, 4, 5, 6, 7
     */
    @Override
    default Iterator<T> iterator() {
        if (isEmpty()) {
            return Iterator.empty();
        } else {
            final Node<T> that = (Node<T>) this;
            return new AbstractIterator<T>() {

                Stack<Node<T>> stack = pushLeftChildren(Stack.empty(), that);

                @Override
                public boolean hasNext() {
                    return !stack.isEmpty();
                }

                @Override
                public T next() {
                    if (!hasNext()) {
                        EMPTY.next();
                    }
                    final Tuple2<Node<T>, ? extends Stack<Node<T>>> result = stack.pop2();
                    final Node<T> node = result._1;
                    stack = node.right.isEmpty() ? result._2 : pushLeftChildren(result._2, (Node<T>) node.right);
                    return result._1.value;
                }

                private Stack<Node<T>> pushLeftChildren(Stack<Node<T>> initialStack, Node<T> that) {
                    Stack<Node<T>> stack = initialStack;
                    RedBlackTree<T> tree = that;
                    while (!tree.isEmpty()) {
                        final Node<T> node = (Node<T>) tree;
                        stack = stack.push(node);
                        tree = node.left;
                    }
                    return stack;
                }
            };
        }
    }

    /**
     * Compares color, value and sub-trees. The comparator is not compared because function equality is not computable.
     *
     * @return The hash code of this tree.
     */
    @Override
    boolean equals(Object o);

    /**
     * Computes the hash code of this tree based on color, value and sub-trees. The comparator is not taken into account.
     *
     * @return The hash code of this tree.
     */
    @Override
    int hashCode();

    /**
     * Returns a Lisp like representation of this tree.
     *
     * @return This Tree as Lisp like String.
     */
    @Override
    String toString();

    enum Color {

        RED, BLACK;

        @Override
        public String toString() {
            return (this == RED) ? "R" : "B";
        }
    }

    /**
     * A non-empty tree node.
     *
     * @param <T> Component type
     */
    class Node<T> implements RedBlackTree<T>, Serializable {

        private static final long serialVersionUID = 1L;

        public final Color color;
        public final int blackHeight;
        public final RedBlackTree<T> left;
        public final T value;
        public final RedBlackTree<T> right;
        public final Empty<T> empty;

        private final transient Lazy<Integer> hashCode;

        // This is no public API! The RedBlackTree takes care of passing the correct Comparator.
        private Node(Color color, int blackHeight, RedBlackTree<T> left, T value, RedBlackTree<T> right, Empty<T> empty) {
            this.color = color;
            this.blackHeight = blackHeight;
            this.left = left;
            this.value = value;
            this.right = right;
            this.empty = empty;
            this.hashCode = Lazy.of(() -> Objects.hash(this.value, this.left, this.right));
        }

        @Override
        public Empty<T> clear() {
            return empty;
        }

        @Override
        public Comparator<? super T> comparator() {
            return empty.comparator;
        }

        @Override
        public boolean contains(T value) {
            final int result = empty.comparator.compare(value, this.value);
            if (result < 0) {
                return left.contains(value);
            } else if (result > 0) {
                return right.contains(value);
            } else {
                return true;
            }
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o instanceof Node) {
                final Node<?> that = (Node<?>) o;
                return Objects.equals(this.value, that.value)
                        && this.left.equals(that.left)
                        && this.right.equals(that.right);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return hashCode.get();
        }

        @Override
        public String toString() {
            return isLeaf() ? "(" + color + ":" + value + ")" : toLispString(this);
        }

        private static String toLispString(RedBlackTree<?> tree) {
            if (tree.isEmpty()) {
                return "";
            } else {
                final Node<?> node = (Node<?>) tree;
                final String value = node.color + ":" + node.value;
                if (node.isLeaf()) {
                    return value;
                } else {
                    final String left = node.left.isEmpty() ? "" : " " + toLispString(node.left);
                    final String right = node.right.isEmpty() ? "" : " " + toLispString(node.right);
                    return "(" + value + left + right + ")";
                }
            }
        }

        private boolean isLeaf() {
            return left.isEmpty() && right.isEmpty();
        }

        private Node<T> color(Color color) {
            return (this.color == color) ? this : new Node<>(color, blackHeight, left, value, right, empty);
        }

        private static <T> RedBlackTree<T> color(RedBlackTree<T> tree, Color color) {
            return tree.isEmpty() ? tree : ((Node<T>) tree).color(color);
        }

        private static <T> Node<T> balanceLeft(Color color, int blackHeight, RedBlackTree<T> left, T value, RedBlackTree<T> right, Empty<T> empty) {
            if (color == BLACK) {
                if (!left.isEmpty()) {
                    final Node<T> ln = (Node<T>) left;
                    if (ln.color == RED) {
                        if (!ln.left.isEmpty()) {
                            final Node<T> lln = (Node<T>) ln.left;
                            if (lln.color == RED) {
                                final Node<T> newLeft = new Node<>(BLACK, blackHeight, lln.left, lln.value, lln.right, empty);
                                final Node<T> newRight = new Node<>(BLACK, blackHeight, ln.right, value, right, empty);
                                return new Node<>(RED, blackHeight + 1, newLeft, ln.value, newRight, empty);
                            }
                        }
                        if (!ln.right.isEmpty()) {
                            final Node<T> lrn = (Node<T>) ln.right;
                            if (lrn.color == RED) {
                                final Node<T> newLeft = new Node<>(BLACK, blackHeight, ln.left, ln.value, lrn.left, empty);
                                final Node<T> newRight = new Node<>(BLACK, blackHeight, lrn.right, value, right, empty);
                                return new Node<>(RED, blackHeight + 1, newLeft, lrn.value, newRight, empty);
                            }
                        }
                    }
                }
            }
            return new Node<>(color, blackHeight, left, value, right, empty);
        }

        private static <T> Node<T> balanceRight(Color color, int blackHeight, RedBlackTree<T> left, T value, RedBlackTree<T> right, Empty<T> empty) {
            if (color == BLACK) {
                if (!right.isEmpty()) {
                    final Node<T> rn = (Node<T>) right;
                    if (rn.color == RED) {
                        if (!rn.right.isEmpty()) {
                            final Node<T> rrn = (Node<T>) rn.right;
                            if (rrn.color == RED) {
                                final Node<T> newLeft = new Node<>(BLACK, blackHeight, left, value, rn.left, empty);
                                final Node<T> newRight = new Node<>(BLACK, blackHeight, rrn.left, rrn.value, rrn.right, empty);
                                return new Node<>(RED, blackHeight + 1, newLeft, rn.value, newRight, empty);
                            }
                        }
                        if (!rn.left.isEmpty()) {
                            final Node<T> rln = (Node<T>) rn.left;
                            if (rln.color == RED) {
                                final Node<T> newLeft = new Node<>(BLACK, blackHeight, left, value, rln.left, empty);
                                final Node<T> newRight = new Node<>(BLACK, blackHeight, rln.right, rn.value, rn.right, empty);
                                return new Node<>(RED, blackHeight + 1, newLeft, rln.value, newRight, empty);
                            }
                        }
                    }
                }
            }
            return new Node<>(color, blackHeight, left, value, right, empty);
        }

        private static <T> Tuple2<? extends RedBlackTree<T>, Boolean> blackify(RedBlackTree<T> tree) {
            if (tree instanceof Node) {
                final Node<T> node = (Node<T>) tree;
                if (node.color == RED) {
                    return Tuple.of(node.color(BLACK), false);
                }
            }
            return Tuple.of(tree, true);
        }

        private static <T> Tuple2<? extends RedBlackTree<T>, Boolean> delete(RedBlackTree<T> tree, T value) {
            if (tree.isEmpty()) {
                return Tuple.of(tree, false);
            } else {
                final Node<T> node = (Node<T>) tree;
                final int comparison = node.comparator().compare(value, node.value);
                if (comparison < 0) {
                    final Tuple2<? extends RedBlackTree<T>, Boolean> deleted = delete(node.left, value);
                    final RedBlackTree<T> l = deleted._1;
                    final boolean d = deleted._2;
                    if (d) {
                        return Node.unbalancedRight(node.color, node.blackHeight - 1, l, node.value, node.right, node.empty);
                    } else {
                        final Node<T> newNode = new Node<>(node.color, node.blackHeight, l, node.value, node.right, node.empty);
                        return Tuple.of(newNode, false);
                    }
                } else if (comparison > 0) {
                    final Tuple2<? extends RedBlackTree<T>, Boolean> deleted = delete(node.right, value);
                    final RedBlackTree<T> r = deleted._1;
                    final boolean d = deleted._2;
                    if (d) {
                        return Node.unbalancedLeft(node.color, node.blackHeight - 1, node.left, node.value, r, node.empty);
                    } else {
                        final Node<T> newNode = new Node<>(node.color, node.blackHeight, node.left, node.value, r, node.empty);
                        return Tuple.of(newNode, false);
                    }
                } else {
                    if (node.right.isEmpty()) {
                        if (node.color == BLACK) {
                            return blackify(node.left);
                        } else {
                            return Tuple.of(node.left, false);
                        }
                    } else {
                        final Node<T> nodeRight = (Node<T>) node.right;
                        final Tuple3<? extends RedBlackTree<T>, Boolean, T> newRight = deleteMin(nodeRight);
                        final RedBlackTree<T> r = newRight._1;
                        final boolean d = newRight._2;
                        final T m = newRight._3;
                        if (d) {
                            return Node.unbalancedLeft(node.color, node.blackHeight - 1, node.left, m, r, node.empty);
                        } else {
                            final RedBlackTree<T> newNode = new Node<>(node.color, node.blackHeight, node.left, m, r, node.empty);
                            return Tuple.of(newNode, false);
                        }
                    }
                }
            }
        }

        private static <T> Tuple3<? extends RedBlackTree<T>, Boolean, T> deleteMin(Node<T> node) {
            if (node.left.isEmpty()) {
                if (node.color == BLACK) {
                    if (node.right.isEmpty()) {
                        return Tuple.of(node.empty, true, node.value);
                    } else {
                        final Node<T> rightNode = (Node<T>) node.right;
                        return Tuple.of(rightNode.color(BLACK), false, node.value);
                    }
                } else {
                    return Tuple.of(node.right, false, node.value);
                }
            } else {
                final Node<T> nodeLeft = (Node<T>) node.left;
                final Tuple3<? extends RedBlackTree<T>, Boolean, T> newNode = deleteMin(nodeLeft);
                final RedBlackTree<T> l = newNode._1;
                final boolean d = newNode._2;
                final T m = newNode._3;
                if (d) {
                    final Tuple2<Node<T>, Boolean> tD = Node.unbalancedRight(node.color, node.blackHeight - 1, l, node.value, node.right, node.empty);
                    return Tuple.of(tD._1, tD._2, m);
                } else {
                    final Node<T> tD = new Node<>(node.color, node.blackHeight, l, node.value, node.right, node.empty);
                    return Tuple.of(tD, false, m);
                }
            }
        }

        private static <T> Node<T> insert(RedBlackTree<T> tree, T value) {
            if (tree.isEmpty()) {
                final Empty<T> empty = (Empty<T>) tree;
                return new Node<>(RED, 1, empty, value, empty, empty);
            } else {
                final Node<T> node = (Node<T>) tree;
                final int comparison = node.comparator().compare(value, node.value);
                if (comparison < 0) {
                    final Node<T> newLeft = insert(node.left, value);
                    return (newLeft == node.left) ? node : Node.balanceLeft(node.color, node.blackHeight, newLeft, node.value, node.right, node.empty);
                } else if (comparison > 0) {
                    final Node<T> newRight = insert(node.right, value);
                    return (newRight == node.right) ? node : Node.balanceRight(node.color, node.blackHeight, node.left, node.value, newRight, node.empty);
                } else {
                    return node;
                }
            }
        }

        private static boolean isRed(RedBlackTree<?> tree) {
            return !tree.isEmpty() && ((Node<?>) tree).color == RED;
        }

        private static <T> RedBlackTree<T> join(RedBlackTree<T> t1, T value, RedBlackTree<T> t2) {
            if (t1.isEmpty()) {
                return t2.add(value);
            } else if (t2.isEmpty()) {
                return t1.add(value);
            } else {
                final Node<T> n1 = (Node<T>) t1;
                final Node<T> n2 = (Node<T>) t2;
                final int comparison = n1.blackHeight - n2.blackHeight;
                if (comparison < 0) {
                    return Node.joinLT(n1, value, n2, n1.blackHeight).color(BLACK);
                } else if (comparison > 0) {
                    return Node.joinGT(n1, value, n2, n2.blackHeight).color(BLACK);
                } else {
                    return new Node<>(BLACK, n1.blackHeight + 1, n1, value, n2, n1.empty);
                }
            }
        }

        private static <T> Node<T> joinGT(Node<T> n1, T value, Node<T> n2, int h2) {
            if (n1.blackHeight == h2) {
                return new Node<>(RED, h2 + 1, n1, value, n2, n1.empty);
            } else {
                final Node<T> node = joinGT((Node<T>) n1.right, value, n2, h2);
                return Node.balanceRight(n1.color, n1.blackHeight, n1.left, n1.value, node, n2.empty);
            }
        }

        private static <T> Node<T> joinLT(Node<T> n1, T value, Node<T> n2, int h1) {
            if (n2.blackHeight == h1) {
                return new Node<>(RED, h1 + 1, n1, value, n2, n1.empty);
            } else {
                final Node<T> node = joinLT(n1, value, (Node<T>) n2.left, h1);
                return Node.balanceLeft(n2.color, n2.blackHeight, node, n2.value, n2.right, n2.empty);
            }
        }

        private static <T> RedBlackTree<T> merge(RedBlackTree<T> t1, RedBlackTree<T> t2) {
            if (t1.isEmpty()) {
                return t2;
            } else if (t2.isEmpty()) {
                return t1;
            } else {
                final Node<T> n1 = (Node<T>) t1;
                final Node<T> n2 = (Node<T>) t2;
                final int comparison = n1.blackHeight - n2.blackHeight;
                if (comparison < 0) {
                    final Node<T> node = Node.mergeLT(n1, n2, n1.blackHeight);
                    return Node.color(node, BLACK);
                } else if (comparison > 0) {
                    final Node<T> node = Node.mergeGT(n1, n2, n2.blackHeight);
                    return Node.color(node, BLACK);
                } else {
                    final Node<T> node = Node.mergeEQ(n1, n2);
                    return Node.color(node, BLACK);
                }
            }
        }

        private static <T> Node<T> mergeEQ(Node<T> n1, Node<T> n2) {
            if (n1.isEmpty() && n2.isEmpty()) {
                return n1;
            } else {
                final T m = Node.minimum(n2);
                final RedBlackTree<T> t2 = Node.deleteMin(n2)._1;
                final int h2 = t2.isEmpty() ? 0 : ((Node<T>) t2).blackHeight;
                final RedBlackTree<T> rl = ((Node<T>) n1.right).left;
                final T rx = ((Node<T>) n1.right).value;
                final RedBlackTree<T> rr = ((Node<T>) n1.right).right;
                if (n1.blackHeight == h2) {
                    return new Node<>(RED, n1.blackHeight + 1, n1, m, t2, n1.empty);
                } else if (isRed(n1.left)) {
                    final Node<T> node = new Node<>(BLACK, n1.blackHeight, n1.right, m, t2, n1.empty);
                    return new Node<>(RED, n1.blackHeight, Node.color(n1.left, BLACK), n1.value, node, n1.empty);
                } else if (isRed(n1.right)) {
                    final Node<T> left = new Node<>(RED, n1.blackHeight, n1.left, n1.value, rl, n1.empty);
                    final Node<T> right = new Node<>(RED, n1.blackHeight, rr, m, t2, n1.empty);
                    return new Node<>(BLACK, n1.blackHeight, left, rx, right, n1.empty);
                } else {
                    return new Node<>(BLACK, n1.blackHeight, n1.color(RED), m, t2, n1.empty);
                }
            }
        }

        private static <T> Node<T> mergeGT(Node<T> n1, Node<T> n2, int h2) {
            if (n1.blackHeight == h2) {
                return Node.mergeEQ(n1, n2);
            } else {
                final Node<T> node = Node.mergeGT((Node<T>) n1.right, n2, h2);
                return Node.balanceRight(n1.color, n1.blackHeight, n1.left, n1.value, node, n1.empty);
            }
        }

        private static <T> Node<T> mergeLT(Node<T> n1, Node<T> n2, int h1) {
            if (n2.blackHeight == h1) {
                return Node.mergeEQ(n1, n2);
            } else {
                final Node<T> node = Node.mergeLT(n1, (Node<T>) n2.left, h1);
                return Node.balanceLeft(n2.color, n2.blackHeight, node, n2.value, n2.right, n2.empty);
            }
        }

        private static <T> T minimum(Node<T> node) {
            Node<T> curr = node;
            while (!curr.left.isEmpty()) {
                curr = (Node<T>) curr.left;
            }
            return curr.value;
        }

        private static <T> Tuple2<RedBlackTree<T>, RedBlackTree<T>> split(RedBlackTree<T> tree, T value) {
            if (tree.isEmpty()) {
                return Tuple.of(tree, tree);
            } else {
                final Node<T> node = (Node<T>) tree;
                final int comparison = node.comparator().compare(value, node.value);
                if (comparison < 0) {
                    final Tuple2<RedBlackTree<T>, RedBlackTree<T>> split = Node.split(node.left, value);
                    return Tuple.of(split._1, Node.join(split._2, node.value, Node.color(node.right, BLACK)));
                } else if (comparison > 0) {
                    final Tuple2<RedBlackTree<T>, RedBlackTree<T>> split = Node.split(node.left, value);
                    return Tuple.of(Node.join(Node.color(node.left, BLACK), node.value, split._1), split._2);
                } else {
                    return Tuple.of(Node.color(node.left, BLACK), Node.color(node.right, BLACK));
                }
            }
        }

        private static <T> Tuple2<Node<T>, Boolean> unbalancedLeft(Color color, int blackHeight, RedBlackTree<T> left, T value, RedBlackTree<T> right, Empty<T> empty) {
            if (!left.isEmpty()) {
                final Node<T> ln = (Node<T>) left;
                if (ln.color == BLACK) {
                    final Node<T> newNode = Node.balanceLeft(BLACK, blackHeight, ln.color(RED), value, right, empty);
                    return Tuple.of(newNode, color == BLACK);
                } else if (color == BLACK && !ln.right.isEmpty()) {
                    final Node<T> lrn = (Node<T>) ln.right;
                    if (lrn.color == BLACK) {
                        final Node<T> newRightNode = Node.balanceLeft(BLACK, blackHeight, lrn.color(RED), value, right, empty);
                        final Node<T> newNode = new Node<>(BLACK, ln.blackHeight, ln.left, ln.value, newRightNode, empty);
                        return Tuple.of(newNode, false);
                    }
                }
            }
            throw new IllegalStateException(String.format("unbalancedLeft(%s, %s, %s, %s, %s)", color, blackHeight, left, value, right));
        }

        private static <T> Tuple2<Node<T>, Boolean> unbalancedRight(Color color, int blackHeight, RedBlackTree<T> left, T value, RedBlackTree<T> right, Empty<T> empty) {
            if (!right.isEmpty()) {
                final Node<T> rn = (Node<T>) right;
                if (rn.color == BLACK) {
                    final Node<T> newNode = Node.balanceRight(BLACK, blackHeight, left, value, rn.color(RED), empty);
                    return Tuple.of(newNode, color == BLACK);
                } else if (color == BLACK && !rn.left.isEmpty()) {
                    final Node<T> rln = (Node<T>) rn.left;
                    if (rln.color == BLACK) {
                        final Node<T> newLeftNode = Node.balanceRight(BLACK, blackHeight, left, value, rln.color(RED), empty);
                        final Node<T> newNode = new Node<>(BLACK, rn.blackHeight, newLeftNode, rn.value, rn.right, empty);
                        return Tuple.of(newNode, false);
                    }
                }
            }
            throw new IllegalStateException(String.format("unbalancedRight(%s, %s, %s, %s, %s)", color, blackHeight, left, value, right));
        }
    }

    /**
     * The empty tree node. It can't be a singleton because it depends on a {@link Comparator}.
     *
     * @param <T> Component type
     */
    class Empty<T> implements RedBlackTree<T>, Serializable {

        private static final long serialVersionUID = 1L;

        public final Comparator<? super T> comparator;

        // This is no public API! The RedBlackTree takes care of passing the correct Comparator.
        private Empty(Comparator<? super T> comparator) {
            this.comparator = comparator;
        }

        @Override
        public Empty<T> clear() {
            return this;
        }

        @Override
        public Comparator<? super T> comparator() {
            return comparator;
        }

        @Override
        public boolean contains(T value) {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean equals(Object o) {
            return (o == this) || (o instanceof Empty);
        }

        @Override
        public int hashCode() {
            return 1;
        }

        @Override
        public String toString() {
            return "()";
        }
    }
}
