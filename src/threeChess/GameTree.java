import java.util.*;

/**
 * Class to represent the possible future game states of any three chess position
 * Each node represents a three chess position, and nodes are linked by legal moves 
 * Nodes will have some object stored in them to faciliate evaluation of positions and/or moves
 * Parameterised for flexibility, nodes will be of the type T
 **/

public class GameTree<T> {

    private T root; //Root node 
    private T traversalNode; //Used to keep track of where we are when traversing the tree

    /**
     * Basic Constructor
     * @param root the node to use as the root for the tree
     **/
    public GameTree(T root) {
        this.root = root;
    }

    /**
     * Class to maintain win/draw/loss statistics for Monte Carlo Tree Search
     * Will store a numerator and denominator to represent this
     * Numerator increased by: 1 for a win, 0.5 for a draw, 0 for a loss
     * Denominator increased by 1 every time a MCTS runoff uses the move associated with this node
     * Each node will have only one parent, and any number of children
     **/
    private class MCNode {

        private MCNode parent = null;
        private Position[2] parentMove; //move taken to traverse from the parent node to this node, assumed to be legal
        private List<MCNode> children = new List<MCNode>();
        private int numerator;
        private int denominator;

        /**
         * Parent-present constructor
         * Stats are set to (0/0)
         * @param parent the parent of this node, null if there isn't one
         * @param parentMove the move linking the parent node to this node
         **/
        public MCNode(MCNode parent, Position[2] parentMove) {
            this.parent = parent;
            this.parentMove = parentMove;
            this.numerator = 0;
            this.denominator = 0;
        }

        //Getters and setters
        public MCNode getParent() {return this.parent;}
        public List<MCNode> getChildren() {return this.children;}
        public int getNumerator() {return this.numerator;}
        public int getDenominator() {return this.denominator;}
        

        public boolean hasParent() {
            return this.parent == null;
        }


    }

}