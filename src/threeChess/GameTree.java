package threeChess;
import java.util.*;
import java.lang.Math;


/**
 * Class to represent the possible future game states of any three chess position
 * Each node represents a three chess position, and nodes are linked by legal moves 
 * Nodes will have some object stored in them to faciliate evaluation of positions and/or moves
 * Parameterised for flexibility, nodes will be of the type T
 * Includes functionality to replace the tree with one of its subtrees
 **/

public class GameTree {

    private MCNode root; //Root node 
    private Colour rootPlayer; //Keeps track of whose ply it is in the root game state
    private MCNode traversalNode; //Keep track of location in tree when traversing
    private int traversalDepth; //Keeps track of how many ply deep the current traversal is

    /**
     * Basic Constructor
     * @param root the node to use as the root for the tree
     * @param player the current turn for the root game state
     **/
    public GameTree(MCNode root, Colour player) {
        this.root = root;
        this.traversalNode = root;
        this.traversalDepth = 0;
        this.rootPlayer = player;
    }

    /**
    * Tree Init Constructor
    * @param player the current turn for the root game state
    **/
   public GameTree(Colour player) {
       this.root = new MCNode(null, null);
       this.traversalNode = root;
       this.traversalDepth = 0;
       this.rootPlayer = player;
   }

    //Getters and setters -------------------------------------------------------------------------------

    public MCNode getRoot() {return this.root;}
    public MCNode getCurrentNode() {return this.traversalNode;}
    public Colour getRootPlayer() {return this.rootPlayer;}
    public int getTraversalDepth() {return this.traversalDepth;}
    public Colour getTraversalPlayer() {return Colour.values()[(this.rootPlayer.ordinal() + this.traversalDepth) % 3];}

    // Traversal -------------------------------------------------------------------------------

    public void resetTraversal() {this.traversalNode = this.root; this.traversalDepth = 0;}

    /**
     * Method to traverse the tree in a single move
     * @param move the ply used to make the next step along the tree (assumed to be valid + legal)
     * @return true if traversal successfully occured, false if no such child was found
     **/
    public boolean traverse(Position[] move) {
        MCNode node = traversalNode.getChild(move);

        if (node == null) {
            return false;
        } else {
            traversalNode = node;
            this.traversalDepth++;
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
            MCNode node = traversalNode.getChild(move);

            if (node == null) {
                return false;
            } else {
                traversalNode = node;
                this.traversalDepth++;
            }
        }

        return true;
    }

    //MCTS Methods ----------------------------------------------------------------------------------------
    
    /**
     * Given the current tree, select a leaf node to expand
     * Choose successive child nodes by applying the UCT formula at each step
     */
    private void selection() {

    }

    //Miscellaneous Methods -------------------------------------------------------------------------------

    /**
     * Method to replace the current tree with one of its subtrees
     * @param newRoot specifies a node of the current tree to become the new root
     * All nodes not part of the subtree are discarded
     **/
    public void pruneTree(MCNode newRoot) {
        this.root = newRoot;
        this.rootPlayer = Colour.values()[(this.rootPlayer.ordinal() + 1) % 3];
    }

    /**
     * Class to maintain win/draw/loss statistics for Monte Carlo Tree Search
     * Will store a numerator and denominator to represent this
     * Numerator increased by: 1 for a win, 0.5 for a draw, 0 for a loss
     * Denominator increased by 1 every time a MCTS runoff uses the move associated with this node
     * Note that ALL ply (reprsented by Position[2]) are assumed to be valid and legal on the corresponding board 
     **/
    private class MCNode {

        private MCNode parent = null;
        private Position[] parentMove = new Position[2]; //move taken to traverse from the parent node to this node, assumed to be legal
        private ArrayList<MCNode> children = new ArrayList<MCNode>();
        private double numerator;
        private double denominator;
        private double uctResult; //Store the UCT formula result here and update it as needed

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

        public MCNode getParent() {return this.parent;}
        public Position[] getMove() {return this.parentMove;}
        public ArrayList<MCNode> getChildren() {return this.children;}
        public double getNumerator() {return this.numerator;}
        public double getDenominator() {return this.denominator;}
        public double getFraction() {return this.numerator / this.denominator;}
        public double getUCT() {return this.uctResult;}

        /**
         * Return the child node that corresponds to the threechess position that would arise after the ply described by
         * move is played on the position represented by the current node
         * @param move the ply to use to select the child
         * @param createIfNone if true, create a child node if the search yields nothing
         * @return the corresponding child node, null if the ply hasn't yet been expanded
         */
        public MCNode getChild(Position[] move, boolean createIfNone) {

            for (MCNode child : this.children) {
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
        public MCNode getChild(Position[] move) {

            for (MCNode child : this.children) {
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
        public void runoffUpdate(double result) {
            this.denominator++;
            this.numerator += result;
            this.updateUCTFormula();

            for (MCNode c : this.children) {
                c.updateUCTFormula();
            }
        
        }

        /**
         * Sets the parent node for this node
         * @param parent the new parent node
         * @throws IllegalArgumentException if parent is null
         */
        public void setParent(MCNode parent) {
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
        public void addChild(MCNode child) {
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

        /**
         * Calculates the UCT forumla for a given node
         * @param node node to use in calculations
         * @return the result of the formula
         */
        public void updateUCTFormula() {
            this.uctResult = this.getFraction() + Math.sqrt(2*Math.log(this.parent.getDenominator()) / this.denominator);
        }


    }

}