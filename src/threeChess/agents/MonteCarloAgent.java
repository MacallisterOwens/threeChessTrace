package threeChess.agents;

import threeChess.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Map.Entry;


public class MonteCarloAgent extends Agent {

    private static final String name = "Carlos the Monty";
    private boolean initialised; //Remember if we've initialised everything
    private Board workingBoard; //board to modify while traversing the game tree to validate moves
    //Manually keep track of piece position to avoid calling getPositions()
    private HashMap<Piece, Position> redPieceLocations; 
    private HashMap<Piece, Position> bluePieceLocations; 
    private HashMap<Piece, Position> greenPieceLocations; 
    //Store piece locations to enable fast lookups for selection phase of MCTS
    private HashMap<Piece, Position> redWorkingPieceLocations; 
    private HashMap<Piece, Position> blueWorkingPieceLocations; 
    private HashMap<Piece, Position> greenWorkingPieceLocations; 
    private GameTree gameTree;

    public Position[] playMove(Board board) {

        this.initialiseAgent(board);

        return null;
    }


    // MCTS Methods ----------------------------------------------------------------------------

    /**
     * Choose a child node to expand to game completion
     */
    private void selection() {

        //Traverse the tree until we reach a twig node
        Position[] nextMove;
        while(true) {

            //A 'twig' node is any one that has not yet been fully expanded
            //Check that here

            nextMove = gameTree.uctSelectChild();
            this.gameTree.traverse(nextMove);
        }
    }

    /**
     * Create a new child node for the current traversal node
     */
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
     * Uses the current workingBoard implementation to inform the changes
     * @param moves the moves taken since the last update, 
     */
    private void updateWorkingPieceLocations(Position[][] moves) {
        for (Position[] move : moves) {
            
        }
    }

    /**
     * Reset workingPieceLocations to match the initila game state
     */
    private void resetWorkingPieceLocations() {
        this.redWorkingPieceLocations = new HashMap<Piece, Position>();
        this.blueWorkingPieceLocations = new HashMap<Piece, Position>();
        this.greenWorkingPieceLocations = new HashMap<Piece, Position>();
        this.redWorkingPieceLocations.putAll(this.redPieceLocations);
        this.blueWorkingPieceLocations.putAll(this.bluePieceLocations);
        this.greenWorkingPieceLocations.putAll(this.greenPieceLocations);
    }

    /**Method to initliase the pieceLocation HashMap
     * @param board the game state to inform the initialization
     */
    private void initPieceLocations(Board board) {
        this.redPieceLocations = new HashMap<Piece, Position>();
        this.bluePieceLocations = new HashMap<Piece, Position>();
        this.greenPieceLocations = new HashMap<Piece, Position>();
        //Use the board getPositions() method to add each players pieces to each hashmap
        //Need to reverse the representation returned by getPositions (<Position, Piece> to <Piece, Position>)
        for (Position p : board.getPositions(Colour.RED)) {
            this.redPieceLocations.put(board.getPiece(p), p);
        } for (Position p : board.getPositions(Colour.BLUE)) {
            this.bluePieceLocations.put(board.getPiece(p), p);
        } for (Position p : board.getPositions(Colour.GREEN)) {
            this.greenPieceLocations.put(board.getPiece(p), p);
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

    /**
     * Returns a set of all availble moves for a given piece based on the working board
     * Moves are represented with the standard {start, end} position array
     * @param position representation of where the given piece is located on the board
     * @return a set containing all possible moves for a given piece
     *
     **/
    private HashSet<Position[]> getAvailableMoves(Position position) {

    Piece mover = this.workingBoard.getPiece(position);
    PieceType moverType = mover.getType();

    HashSet<Position[]> possibleMoves= new HashSet<Position[]>();
    Position p;

    for (Direction[] moves : moverType.getSteps()) { //iterate over every possible step a piece can make

        p = position;

        for (int i = 1; i <= moverType.getStepReps(); i++) { //iterate that step as many times as possible to check every possible move
            try { //try the move
                p = this.workingBoard.step(mover, moves, p);
                if (this.workingBoard.isLegalMove(position, p)) possibleMoves.add(new Position[] {position, p}); //move is good, add it to the list
            } catch(ImpossiblePositionException e) { 
                break; //ended up in an illegal position, abandon that step and try another
            }

        }
    }

    return possibleMoves;

    }

    /**
     * Returns all possible moves a player can make based on the working board
     */
    private HashSet<Position[]> getAllAvailableMoves(Colour player) {

        HashSet<Position[]> allMoves = new HashSet<Position[]>();
        HashMap<Piece, Position> pieceLocations = new HashMap<Piece, Position>();

        switch (player) {
            case RED:
                pieceLocations = this.redWorkingPieceLocations;
                break;
            case BLUE:
                pieceLocations = this.blueWorkingPieceLocations;
                break;
            case GREEN:
                pieceLocations = this.greenWorkingPieceLocations;
                break;    
        }

        for (Position p : pieceLocations.values()) {
            allMoves.addAll(this.getAvailableMoves(p));
        }

        return allMoves;

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