package threeChess;
import java.util.*;

/**
 * Class to represent the possible future game states of any three chess position
 * Each node represents a three chess position, and nodes are linked by legal moves 
 * Nodes will have some object stored in them to faciliate evaluation of positions and/or moves
 * Parameterised for flexibility, nodes will be of the type T
 * Includes functionality to replace the tree with one of its subtrees
 **/

public class GameTree {

    /**
     * Node interface to allow for parameterisation of GameTree
     * Nodes have one parent and any number of children
     * Each nodes stores the ply that immediately precedes it as Position[]
     * As such, adjacent nodes are linked by a size-2 Position array
     */
    private interface Node {
        public Node getParent();
        public void setParent(Node N);
        public Node getChild(Position[] P);
        public void addChild(Node N);
        public ArrayList<Node> getChildren();
        public Position[] getMove();
    }

    private Node root; //Root node 
    private Node traversalNode; //Keep track of location in tree when traversing

    /**
     * Basic Constructor
     * @param root the node to use as the root for the tree
     **/
    public GameTree(Node root) {
        this.root = root;
        this.traversalNode = root;
    }

    //Getters and setters -------------------------------------------------------------------------------

    public Node getRoot() {return this.root;}
    public Node getCurrentNode() {return this.traversalNode;}

    // Traversal -------------------------------------------------------------------------------

    public void resetTraversal() {this.traversalNode = this.root;}

    /**
     * Method to traverse the tree in a single move
     * @param move the ply used to make the next step along the tree (assumed to be valid + legal)
     * @return true if traversal successfully occured, false if no such child was found
     **/
    public boolean traverse(Position[] move) {
        Node node = traversalNode.getChild(move);

        if (node == null) {
            return false;
        } else {
            traversalNode = node;
            return true;
        }

    }

    /**
     * Method to traverse the tree in multiple moves
     * Overloaded
     * @param move an array of ply represenattions used to make the steps along the tree ( all assumed to be valid + legal)
     * @return true if traversal successfully finished, false if it halted at any point
     **/
    public boolean traverse(Position[][] moves) {

        for (Position[] move : moves) {
            Node node = traversalNode.getChild(move);

            if (node == null) {
                return false;
            } else {
                traversalNode = node;
            }
        }

        return true;
    }

    //Miscellaneous Methods -------------------------------------------------------------------------------

    /**
     * Method to replace the current tree with one of its subtrees
     * @param newRoot specifies a node of the current tree to become the new root
     * All nodes not part of the subtree are discarded
     **/
    public void pruneTree(Node newRoot) {
        this.root = newRoot;
    }

    /**
     * Class to maintain win/draw/loss statistics for Monte Carlo Tree Search
     * Will store a numerator and denominator to represent this
     * Numerator increased by: 1 for a win, 0.5 for a draw, 0 for a loss
     * Denominator increased by 1 every time a MCTS runoff uses the move associated with this node
     * Note that ALL ply (reprsented by Position[2]) are assumed to be valid and legal on the corresponding board 
     **/
    private class MCNode implements Node {

        private Node parent = null;
        private Position[] parentMove = new Position[2]; //move taken to traverse from the parent node to this node, assumed to be legal
        private ArrayList<Node> children = new ArrayList<Node>();
        private double numerator;
        private double denominator;

        /**
         * Parent-present constructor
         * Stats are set to (0/0)
         * @param parent the parent of this node, null if there isn't one
         * @param parentMove the move linking the parent node to this node
         **/
        public MCNode(MCNode parent, Position[] parentMove) {
            this.parent = parent;
            this.parentMove = parentMove;
            this.numerator = 0.0;
            this.denominator = 0.0;
        }

        //Getters and setters -------------------------------------------------------------------------------

        public Node getParent() {return this.parent;}
        public Position[] getMove() {return this.parentMove;}
        public ArrayList<Node> getChildren() {return this.children;}
        public double getNumerator() {return this.numerator;}
        public double getDenominator() {return this.denominator;}
        public double getFraction() {return this.numerator / this.denominator;}

        /**
         * Return the child node that corresponds to the threechess position that would arise after the ply described by
         * move is played on the position represented by the current node
         * @param move the ply to use to select the child
         * @param createIfNone if true, create a child node if the search yields nothing
         * @return the corresponding child node, null if the ply hasn't yet been expanded
         */
        public Node getChild(Position[] move, boolean createIfNone) {

            for (Node child : this.children) {
                if (Arrays.equals(child.getMove(), move)) {
                    return child;
                }
            }

            if (createIfNone) {
                //Child node does not yet exist, create and add it
                MCNode c = new MCNode(this, move);
                this.children.add(c);

                return c;
            } else {
                return null;
            }

        }

        /**
         * Return the child node that corresponds to the threechess position that would arise after the ply described by
         * move is played on the position represented by the current node
         * Method overloading to default to non-creation behaviour
         * @param move the ply to use to select the child
         * @return the corresponding child node, null if the ply hasn't yet been expanded
         */
        public Node getChild(Position[] move) {

            for (Node child : this.children) {
                if (Arrays.equals(child.getMove(), move)) {
                    return child;
                }
            }

            return null;

        }


        //Info about this node -------------------------------------------------------------------------------

        public boolean hasParent() {return this.parent == null;}
        public int numChildren() {return this.children.size();}
        public boolean isLeaf() {return this.children.size() == 0;}

        /**
         * Updater for MC runoffs
         * @param result enumerates the result of the relevant runoff (1 = win, 0.5 = draw, 0 = loss)
         **/
        public void runoffUpdate(double result) {this.denominator++; this.numerator += result;}

        /**
         * Sets the parent node for this node
         * @param parent the new parent node
         * @throws IllegalArgumentException if parent is null
         */
        public void setParent(Node parent) {
            if (parent == null) throw new IllegalArgumentException("bad node sent to setParent, cannot be null");
            this.parent = parent;
        }


        //Miscellaneous Methods -------------------------------------------------------------------------------

        /**
         * Add an exisiting node as a child to this node
         * Note that any tree structure inherited from the new child will be maintained
         * As such, be mindful of creating loops in the tree
         * @param child the node to be added
         * @throws IllegalArgumentException if child is null 
         **/
        public void addChild(Node child) {
            if (child == null) throw new IllegalArgumentException("bad node sent to addChild, cannot be null");
            this.children.add(child);
            child.setParent(this);
        }

        /**
         * Add a new node as a child to this node
         * @param move the ply used to get from this node to the child node
         **/
        public void addChild(Position[] move) {
            MCNode child = new MCNode(this, move);
            this.children.add(child);
        }


    }

}