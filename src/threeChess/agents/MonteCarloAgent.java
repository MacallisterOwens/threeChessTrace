package threeChess.agents;

import threeChess.*;

import java.io.*;
import java.util.*;


public class MonteCarloAgent extends Agent {

    private static final String name = "Carlos the Monty";
    private GameTree gameTree;

    public Position[] playMove(Board board) {

        return null;
    }

    private void selection() {

    }

    private void expansion() {
        
    }

    private void rollout() {
        
    }

    private void backpropogate() {
        
    }

    /**
     * @return the Agent's name, for annotating game description.
     * **/ 
    public String toString(){return name;}

    /**
     * Displays the final board position to the agent, 
     * if required for learning purposes. 
     * Other a default implementation may be given.
     * @param finalBoard the end position of the board
     * **/
    public void finalBoard(Board finalBoard){}

}