package threeChess.agents;

import threeChess.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Map.Entry;


public class MonteCarloAgent extends Agent {

    private static final String name = "Carlos the Monty";
    private boolean initialised; //Remember if we've initialised everything
    private Board workingBoard; //board to modify while traversing the game tree to validate moves
    private HashMap<Piece, Position> pieceLocations; //Store piece locations in the position we are given to move from
    private HashMap<Piece, Position> workingPieceLocations; //Store piece locations to enable fast lookups for move selection
    private GameTree gameTree;

    public Position[] playMove(Board board) {

        this.initialiseAgent(board);

        return null;
    }


    // MCTS Methods ----------------------------------------------------------------------------

    private void expansion() {
        
    }

    private void rollout() {
        
    }

    private void backpropogate() {
        
    }



    //GameTree methods --------------------------------------------------------------------------------------------

    /**
     * Initialises the game tree
     * @param board the game state to use to init the tree
     **/
    private void initGameTree(Board board) {

    }

    //Board, piece and move methods -------------------------------------------------------------------------------

    private boolean validateMove(Position[] move) {return this.workingBoard.isLegalMove(move[0], move[1]);}

    /**
     * Method to update the piece location hashmap after a change to the workingBoard
     * @param moves the moves taken since the last update, 
     */
    private void updateWorkingPieceLocations(Position[][] moves) {

    }

    /**
     * Reset workingPieceLocations to match the initila game state
     */
    private void resetWorkingPieceLocations() {
        this.workingPieceLocations = new HashMap<Piece, Position>();
        this.workingPieceLocations.putAll(this.pieceLocations);
    }

    /**Method to initliase the pieceLocation HashMap
     * @param board the game state to inform the initialization
     */
    private void initPieceLocations(Board board) {
        this.pieceLocations = new HashMap<Piece, Position>();
        //Use the board getPositions() method to add each players pieces to the hashmap
        //Need to reverse the representation returned by getPositions (<Position, Piece> to <Piece, Position>)
        for (Colour c : Colour.values()) {
            for (Position p : board.getPositions(c)) {
                this.pieceLocations.put(board.getPiece(p), p);
            }
        }
    }


    /**
     * Method to get the most recent moves played by the opponents
     * In most scenarios, this will result in the two moves played inbetween turns
     * However, at the start of games, there may be only 0-1 moves played before our turn
     * @param board the game state from which to take the previous moves
     * @return an array containing the previous moves. Empty if no moves were played before the current turn
     */
    private Position[][] getPreviousMoves(Board board) {

        int num_moves = board.getMoveCount();

        switch (num_moves) {
            case 0:
                return new Position[0][0];
            case 1:
                return new Position[][] {board.getMove(0)};
            default:
                //Convert num_moves to an index, then get the two most recent entries
                return new Position[][] {board.getMove((num_moves - 1) - 1), board.getMove(num_moves - 1)};
        }
    }


    // Miscellaneous Methods -------------------------------------------------------------------

    /**
     * Performs one-time initialisation to get everything ready for play
     * @param board the initial board to use for initialisation
     */
    private void initialiseAgent(Board board) {
        if (initialised) return;

        this.gameTree = new GameTree(board.getTurn());
        this.initPieceLocations(board);
        this.resetWorkingPieceLocations();


        this.initialised = true;

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