package threeChess.agents;

import threeChess.*;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;

class StateActionValue implements Serializable {
    /**
     * Auto generated serialVersionUID
     */
    private static final long serialVersionUID = -2710175235150454155L;
    HashSet<Position> state; // Contains all positions of all pieces
    Position[] action; // The action taken in the given state
    int tValue; // The value of every single piece present on the board summed

    public StateActionValue(Board board, Position[] action) {
        state = new HashSet<Position>();
        state.addAll(board.getPositions(Colour.BLUE));
        state.addAll(board.getPositions(Colour.GREEN));
        state.addAll(board.getPositions(Colour.RED));
        this.action = action;
        for (Position pos : state) {
            tValue += board.getPiece(pos).getValue();
        }
    }
}

/**
 * An interface for AI bots to implement. They are simply given a Board object
 * indicating the positions of all pieces, the history of the game and whose
 * turn it is, and they respond with a move, expressed as a pair of positions.
 * 
 * Q-Learning Transition Rule:
 *  Qnew(state_t, action_t) = Qold(state_t, action_t) + learningRate * (reward_t + discountFactor * maxQ(nexst_state, every_possible_action) - Qold(state_t, action_t)) 
 **/
public class QLearningAgent extends Agent {

    private static final String name = "Q-Learning Agent";
    private final double initLearningRate = 1.0;
    private final double dropChange = 0.95;
    private final double dropRate = 1.0;

    Position[] myLastAction;
    Board boardState;

    HashMap<StateActionValue, Double> qTable; // The mapping of every single state-action pair to its reward
    HashMap<StateActionValue, Integer> nTimesExecuted; //

    /**
     * A no argument constructor, required for tournament management.
     **/
    public QLearningAgent() {

    }

    /**
     * Returns a set of all availble moves for a given piece Moves are represented
     * with the standard {start, end} position array
     * 
     * @param board    representation of the current game state
     * @param position representation of where the given piece is located on the
     *                 board
     * @return a set containing all possible moves for a given piece
     *
     **/
    private HashSet<Position[]> getAvailableMoves(Board board, Position position) {
        Piece mover = board.getPiece(position);
        PieceType moverType = mover.getType();

        HashSet<Position[]> possibleMoves = new HashSet<Position[]>();
        Position p;

        for (Direction[] moves : moverType.getSteps()) { // iterate over every possible step a piece can make
            p = position;
            for (int i = 1; i <= moverType.getStepReps(); i++) { // iterate that step as many times as possible to check
                                                                 // every possible move
                try { // try the move
                    p = board.step(mover, moves, p);
                    if (board.isLegalMove(position, p)) {
                        possibleMoves.add(new Position[] { position, p }); // move is good, add it to the list
                    }
                } catch (ImpossiblePositionException e) {
                    break; // ended up in an illegal position, abandon that step and try another
                }

            }
        }
        return possibleMoves;
    }

    /**
     * This function determines the learning rate for the Q function which decreases
     * over time, following a step-based learning schedule
     * 
     * @param nVisited The number of times that the state-action pair has been
     *                 visited
     * @return The learning rate paramater which follows a step-based learning
     *         schedule
     */
    private double getLearningRate(int nVisited) {
        return (initLearningRate * Math.pow(dropChange, (1 + nVisited) / dropRate));
    }

    /**
     * Play a move in the game. The agent is given a Board Object representing the
     * position of all pieces, the history of the game and whose turn it is. They
     * respond with a move represented by a pair (two element array) of positions:
     * the start and the end position of the move.
     * 
     * @param board The representation of the game state.
     * @return a two element array of Position objects, where the first element is
     *         the current position of the piece to be moved, and the second element
     *         is the position to move that piece to.
     **/
    public Position[] playMove(Board board) {
        Position[] pieces = board.getPositions(board.getTurn()).toArray(new Position[0]);
        Position start = pieces[0];
        Position end = pieces[0]; // dummy illegal move
        while (!board.isLegalMove(start, end)) {
            start = pieces[random.nextInt(pieces.length)];
            Piece mover = board.getPiece(start);
            Direction[][] steps = mover.getType().getSteps();
            Direction[] step = steps[random.nextInt(steps.length)];
            int reps = 1 + random.nextInt(mover.getType().getStepReps());
            end = start;
            try {
                for (int i = 0; i < reps; i++)
                    end = board.step(mover, step, end, start.getColour() != end.getColour());
            } catch (ImpossiblePositionException e) {
            }
        }
        return new Position[] { start, end };
    }

    /**
     * @return the Agent's name, for annotating game description.
     **/
    public String toString() {
        return name;
    }

    /**
     * Displays the final board position to the agent, if required for learning
     * purposes. Other a default implementation may be given.
     * 
     * @param finalBoard the end position of the board
     **/
    public void finalBoard(Board finalBoard) {
    }

}
