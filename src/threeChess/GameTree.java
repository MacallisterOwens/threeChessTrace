import java.util.*;

/**
 * Class to represent the possible future game states of any three chess position
 * Each node represents a three chess position, and nodes are linked by legal moves 
 * Nodes will have some object stored in them to faciliate evaluation of positions and/or moves
 * Parameterised for flexibility
 **/

public class GameTree<T> {

    public GameTree() {

    }

    /**
     * Class to maintain win/draw/loss statistics for Monte Carlo Tree Search
     * Will store a numerator and denominator to represent this
     * Each node will have only one parent, and any number of children
     **/
    private class MCNode {

        public MCNode() {
            
        }

    }

}