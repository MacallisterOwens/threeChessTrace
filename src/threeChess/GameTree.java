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
    public boolean traversalAtLeaf() {return this.traversalNode.isLeaf();}
    public boolean traversalAtRoot() {return this.traversalNode == this.root;}

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

    /**
     * Traverse back up the tree
     * @param steps the number of steps to move back up
     * @return true if the traversal succeeded, false otherwise
     */
    public boolean traverseBack(int steps) {
        while (steps > 0) {
            if (this.traversalAtRoot()) return false;
            this.traversalNode = this.traversalNode.getParent();
            this.traversalDepth--;
            steps--;
        }

        return true;

    }

    /**
     * Traverse back up the tree
     * Method overloading, single step
     * @return true if the traversal succeeded, false otherwise
     */
    public boolean traverseBack() {
        return this.traverseBack(1);
    }

    /**
     * Add a new child node to the current traversal node
     * @param move the ply leading to the game state of the new child, assumed to be valid + legal
     * If the node already exists then the expansion will fail  
     * @return true if the expansion succeeded, false otherwise
     */
    public boolean expandTraversalNode(Position[] move) {
        for (MCNode child : this.traversalNode.getChildren()) {
            if (child.getMove()[0] == move[0] && child.getMove()[1] == move[1]) {
                return false;
            }
        }

        this.traversalNode.addChild(move);
        return true;
    }

    //MCTS Methods ----------------------------------------------------------------------------------------
    
    /**
     * Returns a child node of the current traversal node
     * The child is chosen based on its UCT score
     * @return the move linking the traversal node to the child node with the highest UCT score
     * @throws NullPointerException if the traversalNode has no children (i.e. is a leaf node)
     */
    public Position[] uctSelectChild() {

        if (this.traversalNode.isLeaf()) 
        throw new IllegalArgumentException("Traversal node is leaf, cannot get best UCT child");

        double maxUCT = 0.0;
        Position[] bestMove = null;

        for (MCNode child : this.traversalNode.getChildren()) {
            if (child.getUCT() > maxUCT) {
                maxUCT = child.getUCT();
                bestMove = child.getMove();
            }
        }

        return bestMove;

    }

    /**
     * Method that takes the result of a rollout and updates the current traversal node
     * Note that the traversalPlayer Colour does NOT match the Colour associated with the stats at each node
     * The Stat Colour will be the Colour prior to the traversal Colour
     * @param winner the winner of the rollout
     * @param loser the loser of the rollout
     */
    public void updateTraversal(Colour winner, Colour loser) {
        //Some sneaky math to get the Colour of the move that leads to the current traversal node
        //Also avoid a negative index if its the first move and we're at the root
        Colour traversalPlayer = Colour.values()[(3 + this.rootPlayer.ordinal() + this.traversalDepth - 1) % 3];
        if (traversalPlayer == winner) {
            this.traversalNode.rolloutUpdate(1.0);
        } else if (traversalPlayer == loser) {
            this.traversalNode.rolloutUpdate(0.0);
        } else {
            this.traversalNode.rolloutUpdate(0.5);
        }
    }

    /**
     * Return the best current move 
     * @return a child of the root node with the highest denominator
     */
    public Position[] selectMove() {

        double mostVisits = 0;
        Position[] bestMove = null;

        for (MCNode c : this.root.getChildren()) {
            if (c.getDenominator() > mostVisits) {
                mostVisits = c.getDenominator();
                bestMove = c.getMove();
            }
        }
        
        return bestMove;
    }

    /**
     * Updates the tree to keep up with the two most recent ply made by opponents
     * The new root will correspond to the current game state
     * @param moves the two most recent ply, oldest first
     */
    public void mctsPrune(Position[][] moves) {

        this.resetTraversal();

        for (Position[] move : moves) {
            this.traverse(move);
        }

        this.root = this.traversalNode;
        this.resetTraversal();
        this.root.setParent(null);
    }

    //Miscellaneous Methods -------------------------------------------------------------------------------

    /**
     * Class to maintain win/draw/loss statistics for Monte Carlo Tree Search
     * Will store a numerator and denominator to represent this
     * Numerator increased by: 1 for a win, 0.5 for a draw, 0 for a loss
     * Denominator increased by 1 every time a MCTS rollout uses the move associated with this node
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
            this.updateUCT();
        }

        //Getters and setters -------------------------------------------------------------------------------

        public MCNode getParent() {return this.parent;}
        public Position[] getMove() {return this.parentMove;}
        public ArrayList<MCNode> getChildren() {return this.children;}
        public double getNumerator() {return this.numerator;}
        public double getDenominator() {return this.denominator;}
        public double getFraction() {return this.numerator / this.denominator;}
        public double getUCT() {return this.uctResult;}
        public void setDenominator(double d) {this.denominator = d;}

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

        
        /**
         * Sets the parent node for this node
         * @param parent the new parent node
         */
        public void setParent(MCNode parent) {
            this.parent = parent;
        }


        //Info about this node -------------------------------------------------------------------------------

        public boolean hasParent() {return this.parent == null;}
        public int numChildren() {return this.children.size();}
        public boolean isLeaf() {return this.children.size() == 0;}

        /**
         * Updater for MC rollouts
         * @param result enumerates the result of the relevant rollout (1 = win, 0.5 = draw, 0 = loss)
         **/
        public void rolloutUpdate(double result) {
            this.denominator++;
            this.numerator += result;
            this.updateUCT();

            for (MCNode c : this.children) {
                c.updateUCT();
            }
        
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
        public void updateUCT() {
            if (this.denominator == 0) this.uctResult = 100000000;
            else if (this.parent == null) return; //root node, does not need a UCT value
            else this.uctResult = this.getFraction() + Math.sqrt(2*Math.log(this.parent.getDenominator()) / this.denominator);
        }

        /**
         * Checks if two nodes are equal
         * @param node the node to compare with
         * @return true if the two nodes refer to the game state that corresponds to an identical series of ply
         */
        public boolean equals(MCNode node) {
            return (this.parent == node.getParent() && this.parentMove == node.getMove());
        }


    }

}