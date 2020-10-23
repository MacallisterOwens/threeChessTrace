package threeChess.agents;

import threeChess.*;

import java.util.HashSet;
import java.util.Random;

/**
 * An interface for AI bots to implement.
 * They are simply given a Board object indicating the positions of all pieces, 
 * the history of the game and whose turn it is, and they respond with a move, 
 * expressed as a pair of positions.
 * **/ 
public class GreedyAgent extends Agent{
  
  private static final String name = "Greedy";
  private static final Random random = new Random();


  /**
   * A no argument constructor, 
   * required for tournament management.
   * **/
  public GreedyAgent(){
  }

  /**
   * Play a move in the game. 
   * The agent is given a Board Object representing the position of all pieces, 
   * the history of the game and whose turn it is. 
   * They respond with a move represented by a pair (two element array) of positions: 
   * the start and the end position of the move.
   * @param board The representation of the game state.
   * @return a two element array of Position objects, where the first element is the 
   * current position of the piece to be moved, and the second element is the 
   * position to move that piece to.
   * **/
  public Position[] playMove(Board board){
    Board changedBoard = null;
    HashSet<Position> piecePosSet = (HashSet<Position>) board.getPositions(board.getTurn());
    double greed = Double.MIN_VALUE;
    Position[] greedMove = new Position[] {null, null};
    for(Position pos : piecePosSet) {
        try {
            changedBoard = (Board) board.clone();
        } catch (Exception e) {
            return null;
        }
        Piece mover = changedBoard.getPiece(pos);
        PieceType moverType = mover.getType();
        Position p;
        for (Direction[] moves : moverType.getSteps()) { // iterate over every possible step a piece can make
            p = pos;
            for (int i = 1; i <= moverType.getStepReps(); i++) { // iterate that step as many times as possible to check
                                                                 // every possible move
                try { // try the move
                    p = changedBoard.step(mover, moves, p);
                    if (changedBoard.isLegalMove(pos, p)) {
                        if(changedBoard.score(changedBoard.getTurn()) > greed) {
                            greed = changedBoard.score(changedBoard.getTurn());
                            greedMove = new Position[] {pos, p};
                        }
                    }
                } catch (ImpossiblePositionException e) {
                    break; // ended up in an illegal position, abandon that step and try another
                }

            }
        }
    }
    return greedMove;
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


