package threeChess.agents;

import threeChess.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

class StateAction implements Serializable {
    /**
     * Auto generated serialVersionUID
     */
    private static final long serialVersionUID = 811621724494833192L;
    
    Set<Position> state; // Contains all positions of all pieces
    Position[] action; // The action taken in the given state

    public StateAction(Board board, Position[] a) {
        try {
            state = new HashSet<>();
            state.addAll(board.getPositions(Colour.BLUE));
            state.addAll(board.getPositions(Colour.GREEN));
            state.addAll(board.getPositions(Colour.RED));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Broke trying to create a state-action object... yikes");
        }
        /* state = (HashSet<Position>) board.getPositionPieceMap().keySet(); */
        action = a;
    }
}

/**
 * This class represents a learning agent utilizing Q-Learning to play
 * three-person chess.
 */
public class QLearningAgent extends Agent {

    private static final String name = "Q-Learning Agent";
    private static final Random random = new Random();

    private final String qTableStorageBlue = "Q-Table-Storage-Blue"; // Q-Table-Storage file name for when the agent is BLUE
    private final String nTimesExecutedStorageBlue = "n-Times-Executed-Storage-Blue"; // n-Times-Executed-Storage file name for when the agent is BLUE
    private final String qTableStorageGreen = "Q-Table-Storage-Green"; // Q-Table-Storage file name for when the agent is GREEN
    private final String nTimesExecutedStorageGreen = "n-Times-Executed-Storage-Green"; // n-Times-Executed-Storage file name for when the agent is GREEN
    private final String qTableStorageRed = "Q-Table-Storage-Red"; // Q-Table-Storage file name for when the agent is RED
    private final String nTimesExecutedStorageRed = "n-Times-Executed-Storage-Red"; // n-Times-Executed-Storage file name for when the agent is RED

    private final double initLearningRate = 1.0; // The initial learning rate 1.0 == %100
    private final double dropChange = 0.95; // The change in the learning rate per drop
    private final double dropRate = 1.0; // The rate at which the learning rate drops
    private double epsilon = 1.0; // The probability in which we choose to utilize exploration vs exploitation

    private int turnsPlayed;

    Position[] myLastAction; // The last action *I* made
    Board prevBoardState; // The state of the board in the previous move
    Board curBoardState; // The state of the board as it currently is
    double prevReward; // The adjusted (curRewardValue_t - prevRewardValue_t) reward of the previous action
    double curReward; // The adjusted (curRewardValue_t-1 - prevRewardValue_t-1) reward we get for being on the current state from last action
    double prevRewardValue; // The unadjusted (full reward) reward value of the previous action

    HashMap<Position, Double> pawnPV; // Maps the position on the board with a pawn's relative value on that position (Pawn Position Value)
    HashMap<Position, Double> knightPV; // Maps the position on the board with a knight's relative value on that position (Knight Position Value)
    HashMap<Position, Double> bishopPV; // Maps the position on the board with a bishop's relative value on that position (Bishop Position Value)
    HashMap<Position, Double> rookPV; // Maps the position on the board with a rook's relative value on that position (Rook Position Value)
    HashMap<Position, Double> queenPV; // Maps the position on the board with a queen's relative value on that position (Queen Position Value)
    HashMap<Position, Double> kingPV; // Maps the position on the board with a king's relative value on that position (King Position Value)

    boolean hasMoved; // whether we have made our first move in the game yet or not
    Colour myColour; // My agent's colour/turn-identifier

    HashMap<StateAction, Double> qTable; // The mapping of every single state-action pair to its value
    HashMap<StateAction, Integer> nTimesExecuted; // The mapping of every single state-action pair to the number of
                                                  // times that action has been taken in that state

    /**
     * A no argument constructor, required for tournament management.
     **/
    public QLearningAgent() {
        myLastAction = new Position[] { null, null };
        prevBoardState = null;
        curBoardState = null;
        prevReward = 0.0;
        curReward = 0.0;
        prevRewardValue = 0.0;
        hasMoved = false;
        myColour = null;
        turnsPlayed = 0;

        pawnPV = new HashMap<Position, Double>();
        knightPV = new HashMap<Position, Double>();
        bishopPV = new HashMap<Position, Double>();
        rookPV = new HashMap<Position, Double>();
        queenPV = new HashMap<Position, Double>();
        kingPV = new HashMap<Position, Double>();

        qTable = new HashMap<StateAction, Double>();
        nTimesExecuted = new HashMap<StateAction, Integer>();
    }

    
     /* ------------------------------------------------------- Private Helper Functions -------------------------------------------------------*/

    /**
     * This function determines what color we are and loads/sets the correct data
     * sets for the agent.
     * 
     * Note: the reason the data is hard coded in is because for the tourny the
     * only file of ours being used is the {agent}.java file and no support/config
     * files. I'm sorry to whoever decides to read through all of the piece-position
     * value population.
     **/
    private void init() {
        switch (myColour) {
            case BLUE:
                try {
                    FileInputStream qFileIn = new FileInputStream(qTableStorageBlue);
                    ObjectInputStream qObjectIn = new ObjectInputStream(qFileIn);
                    qTable = (HashMap<StateAction, Double>) qObjectIn.readObject();
                    qFileIn.close();
                    qObjectIn.close();
    
                    FileInputStream nFileIn = new FileInputStream(nTimesExecutedStorageBlue);
                    ObjectInputStream nObjectIn = new ObjectInputStream(nFileIn);
                    nTimesExecuted = (HashMap<StateAction, Integer>) nObjectIn.readObject();
                    nFileIn.close();
                    nObjectIn.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.print("Q Table storage or n Times Executed Storage either do not exist or could not be opened.");
                }

                pawnPV.put(Position.BA1, 0.0); pawnPV.put(Position.BA2, 2.0); pawnPV.put(Position.BA3, 2.0); pawnPV.put(Position.BA4, 0.0);
                pawnPV.put(Position.BB1, 0.0); pawnPV.put(Position.BB2, 3.0); pawnPV.put(Position.BB3, -2.0); pawnPV.put(Position.BB4, 0.0);
                pawnPV.put(Position.BC1, 0.0); pawnPV.put(Position.BC2, 3.0); pawnPV.put(Position.BC3, -2.0); pawnPV.put(Position.BC4, 0.0);
                pawnPV.put(Position.BD1, 0.0); pawnPV.put(Position.BD2, -3.5); pawnPV.put(Position.BD3, 0.0); pawnPV.put(Position.BD4, 4.5);
                pawnPV.put(Position.BE1, 0.0); pawnPV.put(Position.BE2, -3.5); pawnPV.put(Position.BE3, 0.0); pawnPV.put(Position.BE4, 4.5);
                pawnPV.put(Position.BF1, 0.0); pawnPV.put(Position.BF2, 3.0); pawnPV.put(Position.BF3, -2.0); pawnPV.put(Position.BF4, 0.0);
                pawnPV.put(Position.BG1, 0.0); pawnPV.put(Position.BG2, 3.0); pawnPV.put(Position.BG3, -2.0); pawnPV.put(Position.BG4, 0.0);
                pawnPV.put(Position.BH1, 0.0); pawnPV.put(Position.BH2, 2.0); pawnPV.put(Position.BH3, 2.0); pawnPV.put(Position.BH4, 0.0);

                pawnPV.put(Position.GH4, 2.0); pawnPV.put(Position.GH3, 2.5); pawnPV.put(Position.GH2, 4.0); pawnPV.put(Position.GH1, 4.2);
                pawnPV.put(Position.GG4, 2.0); pawnPV.put(Position.GG3, 2.5); pawnPV.put(Position.GG2, 4.0); pawnPV.put(Position.GG1, 4.2);
                pawnPV.put(Position.GF4, 2.0); pawnPV.put(Position.GF3, 2.7); pawnPV.put(Position.GF2, 4.0); pawnPV.put(Position.GF1, 4.2);
                pawnPV.put(Position.GE4, 2.0); pawnPV.put(Position.GE3, 3.0); pawnPV.put(Position.GE2, 4.0); pawnPV.put(Position.GE1, 4.2);
                pawnPV.put(Position.GD4, 2.0); pawnPV.put(Position.GD3, 2.5); pawnPV.put(Position.GD2, 4.0); pawnPV.put(Position.GD1, 4.2);
                pawnPV.put(Position.GC4, 2.0); pawnPV.put(Position.GC3, 2.7); pawnPV.put(Position.GC2, 4.0); pawnPV.put(Position.GC1, 4.2);
                pawnPV.put(Position.GB4, 2.0); pawnPV.put(Position.GB3, 2.5); pawnPV.put(Position.GB2, 4.0); pawnPV.put(Position.GB1, 4.2);
                pawnPV.put(Position.GA4, 2.0); pawnPV.put(Position.GA3, 2.5); pawnPV.put(Position.GA2, 4.0); pawnPV.put(Position.GA1, 4.2);

                pawnPV.put(Position.RH4, 2.0); pawnPV.put(Position.RH3, 2.5); pawnPV.put(Position.RH2, 4.0); pawnPV.put(Position.RH1, 4.2);
                pawnPV.put(Position.RG4, 2.0); pawnPV.put(Position.RG3, 2.5); pawnPV.put(Position.RG2, 4.0); pawnPV.put(Position.RG1, 4.2);
                pawnPV.put(Position.RF4, 2.0); pawnPV.put(Position.RF3, 2.7); pawnPV.put(Position.RF2, 4.0); pawnPV.put(Position.RF1, 4.2);
                pawnPV.put(Position.RE4, 2.0); pawnPV.put(Position.RE3, 3.0); pawnPV.put(Position.RE2, 4.0); pawnPV.put(Position.RE1, 4.2);
                pawnPV.put(Position.RD4, 2.0); pawnPV.put(Position.RD3, 2.5); pawnPV.put(Position.RD2, 4.0); pawnPV.put(Position.RD1, 4.2);
                pawnPV.put(Position.RC4, 2.0); pawnPV.put(Position.RC3, 2.7); pawnPV.put(Position.RC2, 4.0); pawnPV.put(Position.RC1, 4.2);
                pawnPV.put(Position.RB4, 2.0); pawnPV.put(Position.RB3, 2.5); pawnPV.put(Position.RB2, 4.0); pawnPV.put(Position.RB1, 4.2);
                pawnPV.put(Position.RA4, 2.0); pawnPV.put(Position.RA3, 2.5); pawnPV.put(Position.RA2, 4.0); pawnPV.put(Position.RA1, 4.2);

                
                knightPV.put(Position.BA1, -3.7); knightPV.put(Position.BA2, -3.5); knightPV.put(Position.BA3, -3.0); knightPV.put(Position.BA4, -3.0);
                knightPV.put(Position.BB1, -3.5); knightPV.put(Position.BB2, -2.0); knightPV.put(Position.BB3, 0.8); knightPV.put(Position.BB4, 0.0);
                knightPV.put(Position.BC1, -3.0); knightPV.put(Position.BC2, 0.0); knightPV.put(Position.BC3, 0.8); knightPV.put(Position.BC4, 1.0);
                knightPV.put(Position.BD1, -3.0); knightPV.put(Position.BD2, 0.0); knightPV.put(Position.BD3, 0.8); knightPV.put(Position.BD4, 1.5);
                knightPV.put(Position.BE1, -3.0); knightPV.put(Position.BE2, 0.0); knightPV.put(Position.BE3, 0.8); knightPV.put(Position.BE4, 1.5);
                knightPV.put(Position.BF1, -3.0); knightPV.put(Position.BF2, 0.0); knightPV.put(Position.BF3, 0.8); knightPV.put(Position.BF4, 1.0);
                knightPV.put(Position.BG1, -3.5); knightPV.put(Position.BG2, -2.0); knightPV.put(Position.BG3, -0.8); knightPV.put(Position.BG4, 0.0);
                knightPV.put(Position.BH1, -3.7); knightPV.put(Position.BH2, -3.5); knightPV.put(Position.BH3, -3.0); knightPV.put(Position.BH4, -3.0);

                knightPV.put(Position.GH4, -3.0); knightPV.put(Position.GH3, -3.0); knightPV.put(Position.GH2, -3.5); knightPV.put(Position.GH1, -3.7);
                knightPV.put(Position.GG4, 0.8); knightPV.put(Position.GG3, 0.0); knightPV.put(Position.GG2, -2.0); knightPV.put(Position.GG1, -3.5);
                knightPV.put(Position.GF4, 1.0); knightPV.put(Position.GF3, 0.8); knightPV.put(Position.GF2, 0.0); knightPV.put(Position.GF1, -3.0);
                knightPV.put(Position.GE4, 1.5); knightPV.put(Position.GE3, 0.8); knightPV.put(Position.GE2, 0.0); knightPV.put(Position.GE1, -3.0);
                knightPV.put(Position.GD4, 1.5); knightPV.put(Position.GD3, 0.8); knightPV.put(Position.GD2, 0.0); knightPV.put(Position.GD1, -3.0);
                knightPV.put(Position.GC4, 1.0); knightPV.put(Position.GC3, 0.8); knightPV.put(Position.GC2, 0.0); knightPV.put(Position.GC1, -3.0);
                knightPV.put(Position.GB4, 0.0); knightPV.put(Position.GB3, 0.8); knightPV.put(Position.GB2, -2.0); knightPV.put(Position.GB1, -3.5);
                knightPV.put(Position.GA4, -3.0); knightPV.put(Position.GA3, -3.0); knightPV.put(Position.GA2, -3.5); knightPV.put(Position.GA1, -3.7);

                knightPV.put(Position.RH4, -3.0); knightPV.put(Position.RH3, -3.0); knightPV.put(Position.RH2, -3.5); knightPV.put(Position.RH1, -3.7);
                knightPV.put(Position.RG4, 0.8); knightPV.put(Position.RG3, 0.0); knightPV.put(Position.RG2, -2.0); knightPV.put(Position.RG1, -3.5);
                knightPV.put(Position.RF4, 1.0); knightPV.put(Position.RF3, 0.8); knightPV.put(Position.RF2, 0.0); knightPV.put(Position.RF1, -3.0);
                knightPV.put(Position.RE4, 1.5); knightPV.put(Position.RE3, 0.8); knightPV.put(Position.RE2, 0.0); knightPV.put(Position.RE1, -3.0);
                knightPV.put(Position.RD4, 1.5); knightPV.put(Position.RD3, 0.8); knightPV.put(Position.RD2, 0.0); knightPV.put(Position.RD1, -3.0);
                knightPV.put(Position.RC4, 1.0); knightPV.put(Position.RC3, 0.8); knightPV.put(Position.RC2, 0.0); knightPV.put(Position.RC1, -3.0);
                knightPV.put(Position.RB4, 0.0); knightPV.put(Position.RB3, 0.8); knightPV.put(Position.RB2, -2.0); knightPV.put(Position.RB1, -3.5);
                knightPV.put(Position.RA4, -3.0); knightPV.put(Position.RA3, -3.0); knightPV.put(Position.RA2, -3.5); knightPV.put(Position.RA1, -3.7);


                bishopPV.put(Position.BA1, -2.0); bishopPV.put(Position.BA2, -1.0); bishopPV.put(Position.BA3, -1.0); bishopPV.put(Position.BA4, -1.0);
                bishopPV.put(Position.BB1, -1.0); bishopPV.put(Position.BB2, 0.5); bishopPV.put(Position.BB3, 0.8); bishopPV.put(Position.BB4, 0.0);
                bishopPV.put(Position.BC1, -1.0); bishopPV.put(Position.BC2, 0.0); bishopPV.put(Position.BC3, 0.8); bishopPV.put(Position.BC4, 0.8);
                bishopPV.put(Position.BD1, -1.0); bishopPV.put(Position.BD2, 0.0); bishopPV.put(Position.BD3, 0.8); bishopPV.put(Position.BD4, 0.8);
                bishopPV.put(Position.BE1, -1.0); bishopPV.put(Position.BE2, 0.0); bishopPV.put(Position.BE3, 0.8); bishopPV.put(Position.BE4, 0.8);
                bishopPV.put(Position.BF1, -1.0); bishopPV.put(Position.BF2, 0.0); bishopPV.put(Position.BF3, 0.8); bishopPV.put(Position.BF4, 0.8);
                bishopPV.put(Position.BG1, -1.0); bishopPV.put(Position.BG2, 0.5); bishopPV.put(Position.BG3, 0.8); bishopPV.put(Position.BG4, 0.0);
                bishopPV.put(Position.BH1, -2.0); bishopPV.put(Position.BH2, -1.5); bishopPV.put(Position.BH3, -1.0); bishopPV.put(Position.BH4, -1.0);

                bishopPV.put(Position.GH4, -1.0); bishopPV.put(Position.GH3, -1.0); bishopPV.put(Position.GH2, -1.5); bishopPV.put(Position.GH1, -2.0);
                bishopPV.put(Position.GG4, 0.0); bishopPV.put(Position.GG3, 0.8); bishopPV.put(Position.GG2, 0.5); bishopPV.put(Position.GG1, -1.0);
                bishopPV.put(Position.GF4, 0.8); bishopPV.put(Position.GF3, 0.8); bishopPV.put(Position.GF2, 0.0); bishopPV.put(Position.GF1, -1.0);
                bishopPV.put(Position.GE4, 0.8); bishopPV.put(Position.GE3, 0.8); bishopPV.put(Position.GE2, 0.0); bishopPV.put(Position.GE1, -1.0);
                bishopPV.put(Position.GD4, 0.8); bishopPV.put(Position.GD3, 0.8); bishopPV.put(Position.GD2, 0.0); bishopPV.put(Position.GD1, -1.0);
                bishopPV.put(Position.GC4, 0.8); bishopPV.put(Position.GC3, 0.8); bishopPV.put(Position.GC2, 0.0); bishopPV.put(Position.GC1, -1.0);
                bishopPV.put(Position.GB4, 0.0); bishopPV.put(Position.GB3, 0.8); bishopPV.put(Position.GB2, 0.5); bishopPV.put(Position.GB1, -1.0);
                bishopPV.put(Position.GA4, -1.0); bishopPV.put(Position.GA3, -1.0); bishopPV.put(Position.GA2, -1.5); bishopPV.put(Position.GA1, -2.0);

                bishopPV.put(Position.RH4, -1.0); bishopPV.put(Position.RH3, -1.0); bishopPV.put(Position.RH2, -1.5); bishopPV.put(Position.RH1, -2.0);
                bishopPV.put(Position.RG4, 0.0); bishopPV.put(Position.RG3, 0.8); bishopPV.put(Position.RG2, 0.5); bishopPV.put(Position.RG1, -1.0);
                bishopPV.put(Position.RF4, 0.8); bishopPV.put(Position.RF3, 0.8); bishopPV.put(Position.RF2, 0.0); bishopPV.put(Position.RF1, -1.0);
                bishopPV.put(Position.RE4, 0.8); bishopPV.put(Position.RE3, 0.8); bishopPV.put(Position.RE2, 0.0); bishopPV.put(Position.RE1, -1.0);
                bishopPV.put(Position.RD4, 0.8); bishopPV.put(Position.RD3, 0.8); bishopPV.put(Position.RD2, 0.0); bishopPV.put(Position.RD1, -1.0);
                bishopPV.put(Position.RC4, 0.8); bishopPV.put(Position.RC3, 0.8); bishopPV.put(Position.RC2, 0.0); bishopPV.put(Position.RC1, -1.0);
                bishopPV.put(Position.RB4, 0.0); bishopPV.put(Position.RB3, 0.8); bishopPV.put(Position.RB2, 0.5); bishopPV.put(Position.RB1, -1.0);
                bishopPV.put(Position.RA4, -1.0); bishopPV.put(Position.RA3, -1.0); bishopPV.put(Position.RA2, -1.5); bishopPV.put(Position.RA1, -2.0);


                rookPV.put(Position.BA1, 0.0); rookPV.put(Position.BA2, -0.5); rookPV.put(Position.BA3, -0.5); rookPV.put(Position.BA4, -0.5);
                rookPV.put(Position.BB1, 0.0); rookPV.put(Position.BB2, 0.0); rookPV.put(Position.BB3, 0.0); rookPV.put(Position.BB4, 0.0);
                rookPV.put(Position.BC1, 0.0); rookPV.put(Position.BC2, 0.0); rookPV.put(Position.BC3, 0.0); rookPV.put(Position.BC4, 0.0);
                rookPV.put(Position.BD1, 0.5); rookPV.put(Position.BD2, 0.0); rookPV.put(Position.BD3, 0.0); rookPV.put(Position.BD4, 0.0);
                rookPV.put(Position.BE1, 0.5); rookPV.put(Position.BE2, 0.0); rookPV.put(Position.BE3, 0.0); rookPV.put(Position.BE4, 0.0);
                rookPV.put(Position.BF1, 0.0); rookPV.put(Position.BF2, 0.0); rookPV.put(Position.BF3, 0.0); rookPV.put(Position.BF4, 0.0);
                rookPV.put(Position.BG1, 0.0); rookPV.put(Position.BG2, 0.0); rookPV.put(Position.BG3, 0.0); rookPV.put(Position.BG4, 0.0);
                rookPV.put(Position.BH1, -0.0); rookPV.put(Position.BH2, -0.5); rookPV.put(Position.BH3, -0.5); rookPV.put(Position.BH4, -0.5);

                rookPV.put(Position.GH4, -0.5); rookPV.put(Position.GH3, -0.5); rookPV.put(Position.GH2, 0.5); rookPV.put(Position.GH1, 0.0);
                rookPV.put(Position.GG4, 0.0); rookPV.put(Position.GG3, 0.0); rookPV.put(Position.GG2, 1.0); rookPV.put(Position.GG1, 0.0);
                rookPV.put(Position.GF4, 0.0); rookPV.put(Position.GF3, 0.0); rookPV.put(Position.GF2, 1.0); rookPV.put(Position.GF1, 0.0);
                rookPV.put(Position.GE4, 0.0); rookPV.put(Position.GE3, 0.0); rookPV.put(Position.GE2, 1.0); rookPV.put(Position.GE1, 0.0);
                rookPV.put(Position.GD4, 0.0); rookPV.put(Position.GD3, 0.0); rookPV.put(Position.GD2, 1.0); rookPV.put(Position.GD1, 0.0);
                rookPV.put(Position.GC4, 0.0); rookPV.put(Position.GC3, 0.0); rookPV.put(Position.GC2, 1.0); rookPV.put(Position.GC1, 0.0);
                rookPV.put(Position.GB4, 0.0); rookPV.put(Position.GB3, 0.0); rookPV.put(Position.GB2, 1.0); rookPV.put(Position.GB1, 0.0);
                rookPV.put(Position.GA4, -0.5); rookPV.put(Position.GA3, -0.5); rookPV.put(Position.GA2, 0.5); rookPV.put(Position.GA1, 0.0);

                rookPV.put(Position.RH4, -0.5); rookPV.put(Position.RH3, -0.5); rookPV.put(Position.RH2, 0.5); rookPV.put(Position.RH1, 0.0);
                rookPV.put(Position.RG4, 0.0); rookPV.put(Position.RG3, 0.0); rookPV.put(Position.RG2, 1.0); rookPV.put(Position.RG1, 0.0);
                rookPV.put(Position.RF4, 0.0); rookPV.put(Position.RF3, 0.0); rookPV.put(Position.RF2, 1.0); rookPV.put(Position.RF1, 0.0);
                rookPV.put(Position.RE4, 0.0); rookPV.put(Position.RE3, 0.0); rookPV.put(Position.RE2, 1.0); rookPV.put(Position.RE1, 0.0);
                rookPV.put(Position.RD4, 0.0); rookPV.put(Position.RD3, 0.0); rookPV.put(Position.RD2, 1.0); rookPV.put(Position.RD1, 0.0);
                rookPV.put(Position.RC4, 0.0); rookPV.put(Position.RC3, 0.0); rookPV.put(Position.RC2, 1.0); rookPV.put(Position.RC1, 0.0);
                rookPV.put(Position.RB4, 0.0); rookPV.put(Position.RB3, 0.0); rookPV.put(Position.RB2, 1.0); rookPV.put(Position.RB1, 0.0);
                rookPV.put(Position.RA4, -0.5); rookPV.put(Position.RA3, -0.5); rookPV.put(Position.RA2, 0.5); rookPV.put(Position.RA1, 0.0);


                queenPV.put(Position.BA1, -1.8); queenPV.put(Position.BA2, -0.9); queenPV.put(Position.BA3, -0.9); queenPV.put(Position.BA4, 0.0);
                queenPV.put(Position.BB1, -0.9); queenPV.put(Position.BB2, 0.0); queenPV.put(Position.BB3, 0.6); queenPV.put(Position.BB4, 0.0);
                queenPV.put(Position.BC1, -0.9); queenPV.put(Position.BC2, 0.6); queenPV.put(Position.BC3, 0.6); queenPV.put(Position.BC4, 0.6);
                queenPV.put(Position.BD1, -0.6); queenPV.put(Position.BD2, 0.6); queenPV.put(Position.BD3, 0.6); queenPV.put(Position.BD4, 0.6);
                queenPV.put(Position.BE1, -0.6); queenPV.put(Position.BE2, 0.6); queenPV.put(Position.BE3, 0.6); queenPV.put(Position.BE4, 0.6);
                queenPV.put(Position.BF1, -0.9); queenPV.put(Position.BF2, 0.6); queenPV.put(Position.BF3, 0.6); queenPV.put(Position.BF4, 0.6);
                queenPV.put(Position.BG1, -0.9); queenPV.put(Position.BG2, 0.0); queenPV.put(Position.BG3, 0.6); queenPV.put(Position.BG4, 0.0);
                queenPV.put(Position.BH1, -1.8); queenPV.put(Position.BH2, -0.9); queenPV.put(Position.BH3, -0.9); queenPV.put(Position.BH4, 0.0);

                queenPV.put(Position.GH4, -0.6); queenPV.put(Position.GH3, -0.9); queenPV.put(Position.GH2, -0.9); queenPV.put(Position.GH1, -1.8);
                queenPV.put(Position.GG4, 0.6); queenPV.put(Position.GG3, 0.0); queenPV.put(Position.GG2, 0.0); queenPV.put(Position.GG1, -0.9);
                queenPV.put(Position.GF4, 0.6); queenPV.put(Position.GF3, 0.6); queenPV.put(Position.GF2, 0.0); queenPV.put(Position.GF1, -0.9);
                queenPV.put(Position.GE4, 0.6); queenPV.put(Position.GE3, 0.6); queenPV.put(Position.GE2, 0.0); queenPV.put(Position.GE1, -0.6);
                queenPV.put(Position.GD4, 0.6); queenPV.put(Position.GD3, 0.6); queenPV.put(Position.GD2, 0.0); queenPV.put(Position.GD1, -0.6);
                queenPV.put(Position.GC4, 0.6); queenPV.put(Position.GC3, 0.6); queenPV.put(Position.GC2, 0.0); queenPV.put(Position.GC1, -0.6);
                queenPV.put(Position.GB4, 0.6); queenPV.put(Position.GB3, 0.0); queenPV.put(Position.GB2, 0.0); queenPV.put(Position.GB1, -0.9);
                queenPV.put(Position.GA4, -0.6); queenPV.put(Position.GA3, -0.9); queenPV.put(Position.GA2, -0.9); queenPV.put(Position.GA1, -1.8);

                queenPV.put(Position.RH4, -0.6); queenPV.put(Position.RH3, -0.9); queenPV.put(Position.RH2, -0.9); queenPV.put(Position.RH1, -1.8);
                queenPV.put(Position.RG4, 0.6); queenPV.put(Position.RG3, 0.0); queenPV.put(Position.RG2, 0.0); queenPV.put(Position.RG1, -0.9);
                queenPV.put(Position.RF4, 0.6); queenPV.put(Position.RF3, 0.6); queenPV.put(Position.RF2, 0.0); queenPV.put(Position.RF1, -0.9);
                queenPV.put(Position.RE4, 0.6); queenPV.put(Position.RE3, 0.6); queenPV.put(Position.RE2, 0.0); queenPV.put(Position.RE1, -0.6);
                queenPV.put(Position.RD4, 0.6); queenPV.put(Position.RD3, 0.6); queenPV.put(Position.RD2, 0.0); queenPV.put(Position.RD1, -0.6);
                queenPV.put(Position.RC4, 0.6); queenPV.put(Position.RC3, 0.6); queenPV.put(Position.RC2, 0.0); queenPV.put(Position.RC1, -0.6);
                queenPV.put(Position.RB4, 0.6); queenPV.put(Position.RB3, 0.0); queenPV.put(Position.RB2, 0.0); queenPV.put(Position.RB1, -0.9);
                queenPV.put(Position.RA4, -0.6); queenPV.put(Position.RA3, -0.9); queenPV.put(Position.RA2, -0.9); queenPV.put(Position.RA1, -1.8);


                kingPV.put(Position.BA1, 2.0); kingPV.put(Position.BA2, 2.0); kingPV.put(Position.BA3, -1.0); kingPV.put(Position.BA4, -2.0);
                kingPV.put(Position.BB1, 3.0); kingPV.put(Position.BB2, 2.0); kingPV.put(Position.BB3, -2.0); kingPV.put(Position.BB4, -3.0);
                kingPV.put(Position.BC1, 1.0); kingPV.put(Position.BC2, 0.0); kingPV.put(Position.BC3, -2.0); kingPV.put(Position.BC4, -3.0);
                kingPV.put(Position.BD1, 0.0); kingPV.put(Position.BD2, 0.0); kingPV.put(Position.BD3, -2.0); kingPV.put(Position.BD4, -4.0);
                kingPV.put(Position.BE1, 0.0); kingPV.put(Position.BE2, 0.0); kingPV.put(Position.BE3, -2.0); kingPV.put(Position.BE4, -4.0);
                kingPV.put(Position.BF1, 1.0); kingPV.put(Position.BF2, 0.0); kingPV.put(Position.BF3, -2.0); kingPV.put(Position.BF4, -3.0);
                kingPV.put(Position.BG1, 3.0); kingPV.put(Position.BG2, 2.0); kingPV.put(Position.BG3, -2.0); kingPV.put(Position.BG4, -3.0);
                kingPV.put(Position.BH1, 2.0); kingPV.put(Position.BH2, 2.0); kingPV.put(Position.BH3, -1.0); kingPV.put(Position.BH4, -2.0);

                kingPV.put(Position.GH4, -3.0); kingPV.put(Position.GH3, -3.0); kingPV.put(Position.GH2, -3.0); kingPV.put(Position.GH1, -3.0);
                kingPV.put(Position.GG4, -4.0); kingPV.put(Position.GG3, -4.0); kingPV.put(Position.GG2, -4.0); kingPV.put(Position.GG1, -4.0);
                kingPV.put(Position.GF4, -4.0); kingPV.put(Position.GF3, -4.0); kingPV.put(Position.GF2, -4.0); kingPV.put(Position.GF1, -4.0);
                kingPV.put(Position.GE4, -5.0); kingPV.put(Position.GE3, -5.0); kingPV.put(Position.GE2, -5.0); kingPV.put(Position.GE1, -5.0);
                kingPV.put(Position.GD4, -5.0); kingPV.put(Position.GD3, -5.0); kingPV.put(Position.GD2, -5.0); kingPV.put(Position.GD1, -5.0);
                kingPV.put(Position.GC4, -4.0); kingPV.put(Position.GC3, -4.0); kingPV.put(Position.GC2, -4.0); kingPV.put(Position.GC1, -4.0);
                kingPV.put(Position.GB4, -4.0); kingPV.put(Position.GB3, -4.0); kingPV.put(Position.GB2, -4.0); kingPV.put(Position.GB1, -4.0);
                kingPV.put(Position.GA4, -3.0); kingPV.put(Position.GA3, -3.0); kingPV.put(Position.GA2, -3.0); kingPV.put(Position.GA1, -3.0);

                kingPV.put(Position.RH4, -3.0); kingPV.put(Position.RH3, -3.0); kingPV.put(Position.RH2, -3.0); kingPV.put(Position.RH1, -3.0);
                kingPV.put(Position.RG4, -4.0); kingPV.put(Position.RG3, -4.0); kingPV.put(Position.RG2, -4.0); kingPV.put(Position.RG1, -4.0);
                kingPV.put(Position.RF4, -4.0); kingPV.put(Position.RF3, -4.0); kingPV.put(Position.RF2, -4.0); kingPV.put(Position.RF1, -4.0);
                kingPV.put(Position.RE4, -5.0); kingPV.put(Position.RE3, -5.0); kingPV.put(Position.RE2, -5.0); kingPV.put(Position.RE1, -5.0);
                kingPV.put(Position.RD4, -5.0); kingPV.put(Position.RD3, -5.0); kingPV.put(Position.RD2, -5.0); kingPV.put(Position.RD1, -5.0);
                kingPV.put(Position.RC4, -4.0); kingPV.put(Position.RC3, -4.0); kingPV.put(Position.RC2, -4.0); kingPV.put(Position.RC1, -4.0);
                kingPV.put(Position.RB4, -4.0); kingPV.put(Position.RB3, -4.0); kingPV.put(Position.RB2, -4.0); kingPV.put(Position.RB1, -4.0);
                kingPV.put(Position.RA4, -3.0); kingPV.put(Position.RA3, -3.0); kingPV.put(Position.RA2, -3.0); kingPV.put(Position.RA1, -3.0);

                break;

            case RED:
                try {
                    FileInputStream qFileIn = new FileInputStream(qTableStorageRed);
                    ObjectInputStream qObjectIn = new ObjectInputStream(qFileIn);
                    qTable = (HashMap<StateAction, Double>) qObjectIn.readObject();
                    qFileIn.close();
                    qObjectIn.close();
    
                    FileInputStream nFileIn = new FileInputStream(nTimesExecutedStorageRed);
                    ObjectInputStream nObjectIn = new ObjectInputStream(nFileIn);
                    nTimesExecuted = (HashMap<StateAction, Integer>) nObjectIn.readObject();
                    nFileIn.close();
                    nObjectIn.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.print("Q Table storage or n Times Executed Storage either do not exist or could not be opened.");
                }

                pawnPV.put(Position.RA1, 0.0); pawnPV.put(Position.RA2, 2.0); pawnPV.put(Position.RA3, 2.0); pawnPV.put(Position.RA4, 0.0);
                pawnPV.put(Position.RB1, 0.0); pawnPV.put(Position.RB2, 3.0); pawnPV.put(Position.RB3, -2.0); pawnPV.put(Position.RB4, 0.0);
                pawnPV.put(Position.RC1, 0.0); pawnPV.put(Position.RC2, 3.0); pawnPV.put(Position.RC3, -2.0); pawnPV.put(Position.RC4, 0.0);
                pawnPV.put(Position.RD1, 0.0); pawnPV.put(Position.RD2, -3.5); pawnPV.put(Position.RD3, 0.0); pawnPV.put(Position.RD4, 4.5);
                pawnPV.put(Position.RE1, 0.0); pawnPV.put(Position.RE2, -3.5); pawnPV.put(Position.RE3, 0.0); pawnPV.put(Position.RE4, 4.5);
                pawnPV.put(Position.RF1, 0.0); pawnPV.put(Position.RF2, 3.0); pawnPV.put(Position.RF3, -2.0); pawnPV.put(Position.RF4, 0.0);
                pawnPV.put(Position.RG1, 0.0); pawnPV.put(Position.RG2, 3.0); pawnPV.put(Position.RG3, -2.0); pawnPV.put(Position.RG4, 0.0);
                pawnPV.put(Position.RH1, 0.0); pawnPV.put(Position.RH2, 2.0); pawnPV.put(Position.RH3, 2.0); pawnPV.put(Position.RH4, 0.0);

                pawnPV.put(Position.GH4, 2.0); pawnPV.put(Position.GH3, 2.5); pawnPV.put(Position.GH2, 4.0); pawnPV.put(Position.GH1, 4.2);
                pawnPV.put(Position.GG4, 2.0); pawnPV.put(Position.GG3, 2.5); pawnPV.put(Position.GG2, 4.0); pawnPV.put(Position.GG1, 4.2);
                pawnPV.put(Position.GF4, 2.0); pawnPV.put(Position.GF3, 2.7); pawnPV.put(Position.GF2, 4.0); pawnPV.put(Position.GF1, 4.2);
                pawnPV.put(Position.GE4, 2.0); pawnPV.put(Position.GE3, 3.0); pawnPV.put(Position.GE2, 4.0); pawnPV.put(Position.GE1, 4.2);
                pawnPV.put(Position.GD4, 2.0); pawnPV.put(Position.GD3, 2.5); pawnPV.put(Position.GD2, 4.0); pawnPV.put(Position.GD1, 4.2);
                pawnPV.put(Position.GC4, 2.0); pawnPV.put(Position.GC3, 2.7); pawnPV.put(Position.GC2, 4.0); pawnPV.put(Position.GC1, 4.2);
                pawnPV.put(Position.GB4, 2.0); pawnPV.put(Position.GB3, 2.5); pawnPV.put(Position.GB2, 4.0); pawnPV.put(Position.GB1, 4.2);
                pawnPV.put(Position.GA4, 2.0); pawnPV.put(Position.GA3, 2.5); pawnPV.put(Position.GA2, 4.0); pawnPV.put(Position.GA1, 4.2);

                pawnPV.put(Position.BH4, 2.0); pawnPV.put(Position.BH3, 2.5); pawnPV.put(Position.BH2, 4.0); pawnPV.put(Position.BH1, 4.2);
                pawnPV.put(Position.BG4, 2.0); pawnPV.put(Position.BG3, 2.5); pawnPV.put(Position.BG2, 4.0); pawnPV.put(Position.BG1, 4.2);
                pawnPV.put(Position.BF4, 2.0); pawnPV.put(Position.BF3, 2.7); pawnPV.put(Position.BF2, 4.0); pawnPV.put(Position.BF1, 4.2);
                pawnPV.put(Position.BE4, 2.0); pawnPV.put(Position.BE3, 3.0); pawnPV.put(Position.BE2, 4.0); pawnPV.put(Position.BE1, 4.2);
                pawnPV.put(Position.BD4, 2.0); pawnPV.put(Position.BD3, 2.5); pawnPV.put(Position.BD2, 4.0); pawnPV.put(Position.BD1, 4.2);
                pawnPV.put(Position.BC4, 2.0); pawnPV.put(Position.BC3, 2.7); pawnPV.put(Position.BC2, 4.0); pawnPV.put(Position.BC1, 4.2);
                pawnPV.put(Position.BB4, 2.0); pawnPV.put(Position.BB3, 2.5); pawnPV.put(Position.BB2, 4.0); pawnPV.put(Position.BB1, 4.2);
                pawnPV.put(Position.BA4, 2.0); pawnPV.put(Position.BA3, 2.5); pawnPV.put(Position.BA2, 4.0); pawnPV.put(Position.BA1, 4.2);


                knightPV.put(Position.RA1, -3.7); knightPV.put(Position.RA2, -3.5); knightPV.put(Position.RA3, -3.0); knightPV.put(Position.RA4, -3.0);
                knightPV.put(Position.RB1, -3.5); knightPV.put(Position.RB2, -2.0); knightPV.put(Position.RB3, 0.8); knightPV.put(Position.RB4, 0.0);
                knightPV.put(Position.RC1, -3.0); knightPV.put(Position.RC2, 0.0); knightPV.put(Position.RC3, 0.8); knightPV.put(Position.RC4, 1.0);
                knightPV.put(Position.RD1, -3.0); knightPV.put(Position.RD2, 0.0); knightPV.put(Position.RD3, 0.8); knightPV.put(Position.RD4, 1.5);
                knightPV.put(Position.RE1, -3.0); knightPV.put(Position.RE2, 0.0); knightPV.put(Position.RE3, 0.8); knightPV.put(Position.RE4, 1.5);
                knightPV.put(Position.RF1, -3.0); knightPV.put(Position.RF2, 0.0); knightPV.put(Position.RF3, 0.8); knightPV.put(Position.RF4, 1.0);
                knightPV.put(Position.RG1, -3.5); knightPV.put(Position.RG2, -2.0); knightPV.put(Position.RG3, -0.8); knightPV.put(Position.RG4, 0.0);
                knightPV.put(Position.RH1, -3.7); knightPV.put(Position.RH2, -3.5); knightPV.put(Position.RH3, -3.0); knightPV.put(Position.RH4, -3.0);

                knightPV.put(Position.GH4, -3.0); knightPV.put(Position.GH3, -3.0); knightPV.put(Position.GH2, -3.5); knightPV.put(Position.GH1, -3.7);
                knightPV.put(Position.GG4, 0.8); knightPV.put(Position.GG3, 0.0); knightPV.put(Position.GG2, -2.0); knightPV.put(Position.GG1, -3.5);
                knightPV.put(Position.GF4, 1.0); knightPV.put(Position.GF3, 0.8); knightPV.put(Position.GF2, 0.0); knightPV.put(Position.GF1, -3.0);
                knightPV.put(Position.GE4, 1.5); knightPV.put(Position.GE3, 0.8); knightPV.put(Position.GE2, 0.0); knightPV.put(Position.GE1, -3.0);
                knightPV.put(Position.GD4, 1.5); knightPV.put(Position.GD3, 0.8); knightPV.put(Position.GD2, 0.0); knightPV.put(Position.GD1, -3.0);
                knightPV.put(Position.GC4, 1.0); knightPV.put(Position.GC3, 0.8); knightPV.put(Position.GC2, 0.0); knightPV.put(Position.GC1, -3.0);
                knightPV.put(Position.GB4, 0.0); knightPV.put(Position.GB3, 0.8); knightPV.put(Position.GB2, -2.0); knightPV.put(Position.GB1, -3.5);
                knightPV.put(Position.GA4, -3.0); knightPV.put(Position.GA3, -3.0); knightPV.put(Position.GA2, -3.5); knightPV.put(Position.GA1, -3.7);

                knightPV.put(Position.BH4, -3.0); knightPV.put(Position.BH3, -3.0); knightPV.put(Position.BH2, -3.5); knightPV.put(Position.BH1, -3.7);
                knightPV.put(Position.BG4, 0.8); knightPV.put(Position.BG3, 0.0); knightPV.put(Position.BG2, -2.0); knightPV.put(Position.BG1, -3.5);
                knightPV.put(Position.BF4, 1.0); knightPV.put(Position.BF3, 0.8); knightPV.put(Position.BF2, 0.0); knightPV.put(Position.BF1, -3.0);
                knightPV.put(Position.BE4, 1.5); knightPV.put(Position.BE3, 0.8); knightPV.put(Position.BE2, 0.0); knightPV.put(Position.BE1, -3.0);
                knightPV.put(Position.BD4, 1.5); knightPV.put(Position.BD3, 0.8); knightPV.put(Position.BD2, 0.0); knightPV.put(Position.BD1, -3.0);
                knightPV.put(Position.BC4, 1.0); knightPV.put(Position.BC3, 0.8); knightPV.put(Position.BC2, 0.0); knightPV.put(Position.BC1, -3.0);
                knightPV.put(Position.BB4, 0.0); knightPV.put(Position.BB3, 0.8); knightPV.put(Position.BB2, -2.0); knightPV.put(Position.BB1, -3.5);
                knightPV.put(Position.BA4, -3.0); knightPV.put(Position.BA3, -3.0); knightPV.put(Position.BA2, -3.5); knightPV.put(Position.BA1, -3.7);


                bishopPV.put(Position.RA1, -2.0); bishopPV.put(Position.RA2, -1.0); bishopPV.put(Position.RA3, -1.0); bishopPV.put(Position.RA4, -1.0);
                bishopPV.put(Position.RB1, -1.0); bishopPV.put(Position.RB2, 0.5); bishopPV.put(Position.RB3, 0.8); bishopPV.put(Position.RB4, 0.0);
                bishopPV.put(Position.RC1, -1.0); bishopPV.put(Position.RC2, 0.0); bishopPV.put(Position.RC3, 0.8); bishopPV.put(Position.RC4, 0.8);
                bishopPV.put(Position.RD1, -1.0); bishopPV.put(Position.RD2, 0.0); bishopPV.put(Position.RD3, 0.8); bishopPV.put(Position.RD4, 0.8);
                bishopPV.put(Position.RE1, -1.0); bishopPV.put(Position.RE2, 0.0); bishopPV.put(Position.RE3, 0.8); bishopPV.put(Position.RE4, 0.8);
                bishopPV.put(Position.RF1, -1.0); bishopPV.put(Position.RF2, 0.0); bishopPV.put(Position.RF3, 0.8); bishopPV.put(Position.RF4, 0.8);
                bishopPV.put(Position.RG1, -1.0); bishopPV.put(Position.RG2, 0.5); bishopPV.put(Position.RG3, 0.8); bishopPV.put(Position.RG4, 0.0);
                bishopPV.put(Position.RH1, -2.0); bishopPV.put(Position.RH2, -1.5); bishopPV.put(Position.RH3, -1.0); bishopPV.put(Position.RH4, -1.0);

                bishopPV.put(Position.GH4, -1.0); bishopPV.put(Position.GH3, -1.0); bishopPV.put(Position.GH2, -1.5); bishopPV.put(Position.GH1, -2.0);
                bishopPV.put(Position.GG4, 0.0); bishopPV.put(Position.GG3, 0.8); bishopPV.put(Position.GG2, 0.5); bishopPV.put(Position.GG1, -1.0);
                bishopPV.put(Position.GF4, 0.8); bishopPV.put(Position.GF3, 0.8); bishopPV.put(Position.GF2, 0.0); bishopPV.put(Position.GF1, -1.0);
                bishopPV.put(Position.GE4, 0.8); bishopPV.put(Position.GE3, 0.8); bishopPV.put(Position.GE2, 0.0); bishopPV.put(Position.GE1, -1.0);
                bishopPV.put(Position.GD4, 0.8); bishopPV.put(Position.GD3, 0.8); bishopPV.put(Position.GD2, 0.0); bishopPV.put(Position.GD1, -1.0);
                bishopPV.put(Position.GC4, 0.8); bishopPV.put(Position.GC3, 0.8); bishopPV.put(Position.GC2, 0.0); bishopPV.put(Position.GC1, -1.0);
                bishopPV.put(Position.GB4, 0.0); bishopPV.put(Position.GB3, 0.8); bishopPV.put(Position.GB2, 0.5); bishopPV.put(Position.GB1, -1.0);
                bishopPV.put(Position.GA4, -1.0); bishopPV.put(Position.GA3, -1.0); bishopPV.put(Position.GA2, -1.5); bishopPV.put(Position.GA1, -2.0);

                bishopPV.put(Position.BH4, -1.0); bishopPV.put(Position.BH3, -1.0); bishopPV.put(Position.BH2, -1.5); bishopPV.put(Position.BH1, -2.0);
                bishopPV.put(Position.BG4, 0.0); bishopPV.put(Position.BG3, 0.8); bishopPV.put(Position.BG2, 0.5); bishopPV.put(Position.BG1, -1.0);
                bishopPV.put(Position.BF4, 0.8); bishopPV.put(Position.BF3, 0.8); bishopPV.put(Position.BF2, 0.0); bishopPV.put(Position.BF1, -1.0);
                bishopPV.put(Position.BE4, 0.8); bishopPV.put(Position.BE3, 0.8); bishopPV.put(Position.BE2, 0.0); bishopPV.put(Position.BE1, -1.0);
                bishopPV.put(Position.BD4, 0.8); bishopPV.put(Position.BD3, 0.8); bishopPV.put(Position.BD2, 0.0); bishopPV.put(Position.BD1, -1.0);
                bishopPV.put(Position.BC4, 0.8); bishopPV.put(Position.BC3, 0.8); bishopPV.put(Position.BC2, 0.0); bishopPV.put(Position.BC1, -1.0);
                bishopPV.put(Position.BB4, 0.0); bishopPV.put(Position.BB3, 0.8); bishopPV.put(Position.BB2, 0.5); bishopPV.put(Position.BB1, -1.0);
                bishopPV.put(Position.BA4, -1.0); bishopPV.put(Position.BA3, -1.0); bishopPV.put(Position.BA2, -1.5); bishopPV.put(Position.BA1, -2.0);


                rookPV.put(Position.RA1, 0.0); rookPV.put(Position.RA2, -0.5); rookPV.put(Position.RA3, -0.5); rookPV.put(Position.RA4, -0.5);
                rookPV.put(Position.RB1, 0.0); rookPV.put(Position.RB2, 0.0); rookPV.put(Position.RB3, 0.0); rookPV.put(Position.RB4, 0.0);
                rookPV.put(Position.RC1, 0.0); rookPV.put(Position.RC2, 0.0); rookPV.put(Position.RC3, 0.0); rookPV.put(Position.RC4, 0.0);
                rookPV.put(Position.RD1, 0.5); rookPV.put(Position.RD2, 0.0); rookPV.put(Position.RD3, 0.0); rookPV.put(Position.RD4, 0.0);
                rookPV.put(Position.RE1, 0.5); rookPV.put(Position.RE2, 0.0); rookPV.put(Position.RE3, 0.0); rookPV.put(Position.RE4, 0.0);
                rookPV.put(Position.RF1, 0.0); rookPV.put(Position.RF2, 0.0); rookPV.put(Position.RF3, 0.0); rookPV.put(Position.RF4, 0.0);
                rookPV.put(Position.RG1, 0.0); rookPV.put(Position.RG2, 0.0); rookPV.put(Position.RG3, 0.0); rookPV.put(Position.RG4, 0.0);
                rookPV.put(Position.RH1, -0.0); rookPV.put(Position.RH2, -0.5); rookPV.put(Position.RH3, -0.5); rookPV.put(Position.RH4, -0.5);

                rookPV.put(Position.GH4, -0.5); rookPV.put(Position.GH3, -0.5); rookPV.put(Position.GH2, 0.5); rookPV.put(Position.GH1, 0.0);
                rookPV.put(Position.GG4, 0.0); rookPV.put(Position.GG3, 0.0); rookPV.put(Position.GG2, 1.0); rookPV.put(Position.GG1, 0.0);
                rookPV.put(Position.GF4, 0.0); rookPV.put(Position.GF3, 0.0); rookPV.put(Position.GF2, 1.0); rookPV.put(Position.GF1, 0.0);
                rookPV.put(Position.GE4, 0.0); rookPV.put(Position.GE3, 0.0); rookPV.put(Position.GE2, 1.0); rookPV.put(Position.GE1, 0.0);
                rookPV.put(Position.GD4, 0.0); rookPV.put(Position.GD3, 0.0); rookPV.put(Position.GD2, 1.0); rookPV.put(Position.GD1, 0.0);
                rookPV.put(Position.GC4, 0.0); rookPV.put(Position.GC3, 0.0); rookPV.put(Position.GC2, 1.0); rookPV.put(Position.GC1, 0.0);
                rookPV.put(Position.GB4, 0.0); rookPV.put(Position.GB3, 0.0); rookPV.put(Position.GB2, 1.0); rookPV.put(Position.GB1, 0.0);
                rookPV.put(Position.GA4, -0.5); rookPV.put(Position.GA3, -0.5); rookPV.put(Position.GA2, 0.5); rookPV.put(Position.GA1, 0.0);

                rookPV.put(Position.BH4, -0.5); rookPV.put(Position.BH3, -0.5); rookPV.put(Position.BH2, 0.5); rookPV.put(Position.BH1, 0.0);
                rookPV.put(Position.BG4, 0.0); rookPV.put(Position.BG3, 0.0); rookPV.put(Position.BG2, 1.0); rookPV.put(Position.BG1, 0.0);
                rookPV.put(Position.BF4, 0.0); rookPV.put(Position.BF3, 0.0); rookPV.put(Position.BF2, 1.0); rookPV.put(Position.BF1, 0.0);
                rookPV.put(Position.BE4, 0.0); rookPV.put(Position.BE3, 0.0); rookPV.put(Position.BE2, 1.0); rookPV.put(Position.BE1, 0.0);
                rookPV.put(Position.BD4, 0.0); rookPV.put(Position.BD3, 0.0); rookPV.put(Position.BD2, 1.0); rookPV.put(Position.BD1, 0.0);
                rookPV.put(Position.BC4, 0.0); rookPV.put(Position.BC3, 0.0); rookPV.put(Position.BC2, 1.0); rookPV.put(Position.BC1, 0.0);
                rookPV.put(Position.BB4, 0.0); rookPV.put(Position.BB3, 0.0); rookPV.put(Position.BB2, 1.0); rookPV.put(Position.BB1, 0.0);
                rookPV.put(Position.BA4, -0.5); rookPV.put(Position.BA3, -0.5); rookPV.put(Position.BA2, 0.5); rookPV.put(Position.BA1, 0.0);


                queenPV.put(Position.RA1, -1.8); queenPV.put(Position.RA2, -0.9); queenPV.put(Position.RA3, -0.9); queenPV.put(Position.RA4, 0.0);
                queenPV.put(Position.RB1, -0.9); queenPV.put(Position.RB2, 0.0); queenPV.put(Position.RB3, 0.6); queenPV.put(Position.RB4, 0.0);
                queenPV.put(Position.RC1, -0.9); queenPV.put(Position.RC2, 0.6); queenPV.put(Position.RC3, 0.6); queenPV.put(Position.RC4, 0.6);
                queenPV.put(Position.RD1, -0.6); queenPV.put(Position.RD2, 0.6); queenPV.put(Position.RD3, 0.6); queenPV.put(Position.RD4, 0.6);
                queenPV.put(Position.RE1, -0.6); queenPV.put(Position.RE2, 0.6); queenPV.put(Position.RE3, 0.6); queenPV.put(Position.RE4, 0.6);
                queenPV.put(Position.RF1, -0.9); queenPV.put(Position.RF2, 0.6); queenPV.put(Position.RF3, 0.6); queenPV.put(Position.RF4, 0.6);
                queenPV.put(Position.RG1, -0.9); queenPV.put(Position.RG2, 0.0); queenPV.put(Position.RG3, 0.6); queenPV.put(Position.RG4, 0.0);
                queenPV.put(Position.RH1, -1.8); queenPV.put(Position.RH2, -0.9); queenPV.put(Position.RH3, -0.9); queenPV.put(Position.RH4, 0.0);

                queenPV.put(Position.GH4, -0.6); queenPV.put(Position.GH3, -0.9); queenPV.put(Position.GH2, -0.9); queenPV.put(Position.GH1, -1.8);
                queenPV.put(Position.GG4, 0.6); queenPV.put(Position.GG3, 0.0); queenPV.put(Position.GG2, 0.0); queenPV.put(Position.GG1, -0.9);
                queenPV.put(Position.GF4, 0.6); queenPV.put(Position.GF3, 0.6); queenPV.put(Position.GF2, 0.0); queenPV.put(Position.GF1, -0.9);
                queenPV.put(Position.GE4, 0.6); queenPV.put(Position.GE3, 0.6); queenPV.put(Position.GE2, 0.0); queenPV.put(Position.GE1, -0.6);
                queenPV.put(Position.GD4, 0.6); queenPV.put(Position.GD3, 0.6); queenPV.put(Position.GD2, 0.0); queenPV.put(Position.GD1, -0.6);
                queenPV.put(Position.GC4, 0.6); queenPV.put(Position.GC3, 0.6); queenPV.put(Position.GC2, 0.0); queenPV.put(Position.GC1, -0.6);
                queenPV.put(Position.GB4, 0.6); queenPV.put(Position.GB3, 0.0); queenPV.put(Position.GB2, 0.0); queenPV.put(Position.GB1, -0.9);
                queenPV.put(Position.GA4, -0.6); queenPV.put(Position.GA3, -0.9); queenPV.put(Position.GA2, -0.9); queenPV.put(Position.GA1, -1.8);

                queenPV.put(Position.BH4, -0.6); queenPV.put(Position.BH3, -0.9); queenPV.put(Position.BH2, -0.9); queenPV.put(Position.BH1, -1.8);
                queenPV.put(Position.BG4, 0.6); queenPV.put(Position.BG3, 0.0); queenPV.put(Position.BG2, 0.0); queenPV.put(Position.BG1, -0.9);
                queenPV.put(Position.BF4, 0.6); queenPV.put(Position.BF3, 0.6); queenPV.put(Position.BF2, 0.0); queenPV.put(Position.BF1, -0.9);
                queenPV.put(Position.BE4, 0.6); queenPV.put(Position.BE3, 0.6); queenPV.put(Position.BE2, 0.0); queenPV.put(Position.BE1, -0.6);
                queenPV.put(Position.BD4, 0.6); queenPV.put(Position.BD3, 0.6); queenPV.put(Position.BD2, 0.0); queenPV.put(Position.BD1, -0.6);
                queenPV.put(Position.BC4, 0.6); queenPV.put(Position.BC3, 0.6); queenPV.put(Position.BC2, 0.0); queenPV.put(Position.BC1, -0.6);
                queenPV.put(Position.BB4, 0.6); queenPV.put(Position.BB3, 0.0); queenPV.put(Position.BB2, 0.0); queenPV.put(Position.BB1, -0.9);
                queenPV.put(Position.BA4, -0.6); queenPV.put(Position.BA3, -0.9); queenPV.put(Position.BA2, -0.9); queenPV.put(Position.BA1, -1.8);


                kingPV.put(Position.RA1, 2.0); kingPV.put(Position.RA2, 2.0); kingPV.put(Position.RA3, -1.0); kingPV.put(Position.RA4, -2.0);
                kingPV.put(Position.RB1, 3.0); kingPV.put(Position.RB2, 2.0); kingPV.put(Position.RB3, -2.0); kingPV.put(Position.RB4, -3.0);
                kingPV.put(Position.RC1, 1.0); kingPV.put(Position.RC2, 0.0); kingPV.put(Position.RC3, -2.0); kingPV.put(Position.RC4, -3.0);
                kingPV.put(Position.RD1, 0.0); kingPV.put(Position.RD2, 0.0); kingPV.put(Position.RD3, -2.0); kingPV.put(Position.RD4, -4.0);
                kingPV.put(Position.RE1, 0.0); kingPV.put(Position.RE2, 0.0); kingPV.put(Position.RE3, -2.0); kingPV.put(Position.RE4, -4.0);
                kingPV.put(Position.RF1, 1.0); kingPV.put(Position.RF2, 0.0); kingPV.put(Position.RF3, -2.0); kingPV.put(Position.RF4, -3.0);
                kingPV.put(Position.RG1, 3.0); kingPV.put(Position.RG2, 2.0); kingPV.put(Position.RG3, -2.0); kingPV.put(Position.RG4, -3.0);
                kingPV.put(Position.RH1, 2.0); kingPV.put(Position.RH2, 2.0); kingPV.put(Position.RH3, -1.0); kingPV.put(Position.RH4, -2.0);

                kingPV.put(Position.GH4, -3.0); kingPV.put(Position.GH3, -3.0); kingPV.put(Position.GH2, -3.0); kingPV.put(Position.GH1, -3.0);
                kingPV.put(Position.GG4, -4.0); kingPV.put(Position.GG3, -4.0); kingPV.put(Position.GG2, -4.0); kingPV.put(Position.GG1, -4.0);
                kingPV.put(Position.GF4, -4.0); kingPV.put(Position.GF3, -4.0); kingPV.put(Position.GF2, -4.0); kingPV.put(Position.GF1, -4.0);
                kingPV.put(Position.GE4, -5.0); kingPV.put(Position.GE3, -5.0); kingPV.put(Position.GE2, -5.0); kingPV.put(Position.GE1, -5.0);
                kingPV.put(Position.GD4, -5.0); kingPV.put(Position.GD3, -5.0); kingPV.put(Position.GD2, -5.0); kingPV.put(Position.GD1, -5.0);
                kingPV.put(Position.GC4, -4.0); kingPV.put(Position.GC3, -4.0); kingPV.put(Position.GC2, -4.0); kingPV.put(Position.GC1, -4.0);
                kingPV.put(Position.GB4, -4.0); kingPV.put(Position.GB3, -4.0); kingPV.put(Position.GB2, -4.0); kingPV.put(Position.GB1, -4.0);
                kingPV.put(Position.GA4, -3.0); kingPV.put(Position.GA3, -3.0); kingPV.put(Position.GA2, -3.0); kingPV.put(Position.GA1, -3.0);

                kingPV.put(Position.BH4, -3.0); kingPV.put(Position.BH3, -3.0); kingPV.put(Position.BH2, -3.0); kingPV.put(Position.BH1, -3.0);
                kingPV.put(Position.BG4, -4.0); kingPV.put(Position.BG3, -4.0); kingPV.put(Position.BG2, -4.0); kingPV.put(Position.BG1, -4.0);
                kingPV.put(Position.BF4, -4.0); kingPV.put(Position.BF3, -4.0); kingPV.put(Position.BF2, -4.0); kingPV.put(Position.BF1, -4.0);
                kingPV.put(Position.BE4, -5.0); kingPV.put(Position.BE3, -5.0); kingPV.put(Position.BE2, -5.0); kingPV.put(Position.BE1, -5.0);
                kingPV.put(Position.BD4, -5.0); kingPV.put(Position.BD3, -5.0); kingPV.put(Position.BD2, -5.0); kingPV.put(Position.BD1, -5.0);
                kingPV.put(Position.BC4, -4.0); kingPV.put(Position.BC3, -4.0); kingPV.put(Position.BC2, -4.0); kingPV.put(Position.BC1, -4.0);
                kingPV.put(Position.BB4, -4.0); kingPV.put(Position.BB3, -4.0); kingPV.put(Position.BB2, -4.0); kingPV.put(Position.BB1, -4.0);
                kingPV.put(Position.BA4, -3.0); kingPV.put(Position.BA3, -3.0); kingPV.put(Position.BA2, -3.0); kingPV.put(Position.BA1, -3.0);

                break;

            case GREEN:
                try {
                    FileInputStream qFileIn = new FileInputStream(qTableStorageGreen);
                    ObjectInputStream qObjectIn = new ObjectInputStream(qFileIn);
                    qTable = (HashMap<StateAction, Double>) qObjectIn.readObject();
                    qFileIn.close();
                    qObjectIn.close();
    
                    FileInputStream nFileIn = new FileInputStream(nTimesExecutedStorageGreen);
                    ObjectInputStream nObjectIn = new ObjectInputStream(nFileIn);
                    nTimesExecuted = (HashMap<StateAction, Integer>) nObjectIn.readObject();
                    nFileIn.close();
                    nObjectIn.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.print("Q Table storage or n Times Executed Storage either do not exist or could not be opened.");
                }

                pawnPV.put(Position.GA1, 0.0); pawnPV.put(Position.GA2, 2.0); pawnPV.put(Position.GA3, 2.0); pawnPV.put(Position.GA4, 0.0);
                pawnPV.put(Position.GB1, 0.0); pawnPV.put(Position.GB2, 3.0); pawnPV.put(Position.GB3, -2.0); pawnPV.put(Position.GB4, 0.0);
                pawnPV.put(Position.GC1, 0.0); pawnPV.put(Position.GC2, 3.0); pawnPV.put(Position.GC3, -2.0); pawnPV.put(Position.GC4, 0.0);
                pawnPV.put(Position.GD1, 0.0); pawnPV.put(Position.GD2, -3.5); pawnPV.put(Position.GD3, 0.0); pawnPV.put(Position.GD4, 4.5);
                pawnPV.put(Position.GE1, 0.0); pawnPV.put(Position.GE2, -3.5); pawnPV.put(Position.GE3, 0.0); pawnPV.put(Position.GE4, 4.5);
                pawnPV.put(Position.GF1, 0.0); pawnPV.put(Position.GF2, 3.0); pawnPV.put(Position.GF3, -2.0); pawnPV.put(Position.GF4, 0.0);
                pawnPV.put(Position.GG1, 0.0); pawnPV.put(Position.GG2, 3.0); pawnPV.put(Position.GG3, -2.0); pawnPV.put(Position.GG4, 0.0);
                pawnPV.put(Position.GH1, 0.0); pawnPV.put(Position.GH2, 2.0); pawnPV.put(Position.GH3, 2.0); pawnPV.put(Position.GH4, 0.0);

                pawnPV.put(Position.BH4, 2.0); pawnPV.put(Position.BH3, 2.5); pawnPV.put(Position.BH2, 4.0); pawnPV.put(Position.BH1, 4.2);
                pawnPV.put(Position.BG4, 2.0); pawnPV.put(Position.BG3, 2.5); pawnPV.put(Position.BG2, 4.0); pawnPV.put(Position.BG1, 4.2);
                pawnPV.put(Position.BF4, 2.0); pawnPV.put(Position.BF3, 2.7); pawnPV.put(Position.BF2, 4.0); pawnPV.put(Position.BF1, 4.2);
                pawnPV.put(Position.BE4, 2.0); pawnPV.put(Position.BE3, 3.0); pawnPV.put(Position.BE2, 4.0); pawnPV.put(Position.BE1, 4.2);
                pawnPV.put(Position.BD4, 2.0); pawnPV.put(Position.BD3, 2.5); pawnPV.put(Position.BD2, 4.0); pawnPV.put(Position.BD1, 4.2);
                pawnPV.put(Position.BC4, 2.0); pawnPV.put(Position.BC3, 2.7); pawnPV.put(Position.BC2, 4.0); pawnPV.put(Position.BC1, 4.2);
                pawnPV.put(Position.BB4, 2.0); pawnPV.put(Position.BB3, 2.5); pawnPV.put(Position.BB2, 4.0); pawnPV.put(Position.BB1, 4.2);
                pawnPV.put(Position.BA4, 2.0); pawnPV.put(Position.BA3, 2.5); pawnPV.put(Position.BA2, 4.0); pawnPV.put(Position.BA1, 4.2);

                pawnPV.put(Position.RH4, 2.0); pawnPV.put(Position.RH3, 2.5); pawnPV.put(Position.RH2, 4.0); pawnPV.put(Position.RH1, 4.2);
                pawnPV.put(Position.RG4, 2.0); pawnPV.put(Position.RG3, 2.5); pawnPV.put(Position.RG2, 4.0); pawnPV.put(Position.RG1, 4.2);
                pawnPV.put(Position.RF4, 2.0); pawnPV.put(Position.RF3, 2.7); pawnPV.put(Position.RF2, 4.0); pawnPV.put(Position.RF1, 4.2);
                pawnPV.put(Position.RE4, 2.0); pawnPV.put(Position.RE3, 3.0); pawnPV.put(Position.RE2, 4.0); pawnPV.put(Position.RE1, 4.2);
                pawnPV.put(Position.RD4, 2.0); pawnPV.put(Position.RD3, 2.5); pawnPV.put(Position.RD2, 4.0); pawnPV.put(Position.RD1, 4.2);
                pawnPV.put(Position.RC4, 2.0); pawnPV.put(Position.RC3, 2.7); pawnPV.put(Position.RC2, 4.0); pawnPV.put(Position.RC1, 4.2);
                pawnPV.put(Position.RB4, 2.0); pawnPV.put(Position.RB3, 2.5); pawnPV.put(Position.RB2, 4.0); pawnPV.put(Position.RB1, 4.2);
                pawnPV.put(Position.RA4, 2.0); pawnPV.put(Position.RA3, 2.5); pawnPV.put(Position.RA2, 4.0); pawnPV.put(Position.RA1, 4.2);


                knightPV.put(Position.GA1, -3.7); knightPV.put(Position.GA2, -3.5); knightPV.put(Position.GA3, -3.0); knightPV.put(Position.GA4, -3.0);
                knightPV.put(Position.GB1, -3.5); knightPV.put(Position.GB2, -2.0); knightPV.put(Position.GB3, 0.8); knightPV.put(Position.GB4, 0.0);
                knightPV.put(Position.GC1, -3.0); knightPV.put(Position.GC2, 0.0); knightPV.put(Position.GC3, 0.8); knightPV.put(Position.GC4, 1.0);
                knightPV.put(Position.GD1, -3.0); knightPV.put(Position.GD2, 0.0); knightPV.put(Position.GD3, 0.8); knightPV.put(Position.GD4, 1.5);
                knightPV.put(Position.GE1, -3.0); knightPV.put(Position.GE2, 0.0); knightPV.put(Position.GE3, 0.8); knightPV.put(Position.GE4, 1.5);
                knightPV.put(Position.GF1, -3.0); knightPV.put(Position.GF2, 0.0); knightPV.put(Position.GF3, 0.8); knightPV.put(Position.GF4, 1.0);
                knightPV.put(Position.GG1, -3.5); knightPV.put(Position.GG2, -2.0); knightPV.put(Position.GG3, -0.8); knightPV.put(Position.GG4, 0.0);
                knightPV.put(Position.GH1, -3.7); knightPV.put(Position.GH2, -3.5); knightPV.put(Position.GH3, -3.0); knightPV.put(Position.GH4, -3.0);

                knightPV.put(Position.BH4, -3.0); knightPV.put(Position.BH3, -3.0); knightPV.put(Position.BH2, -3.5); knightPV.put(Position.BH1, -3.7);
                knightPV.put(Position.BG4, 0.8); knightPV.put(Position.BG3, 0.0); knightPV.put(Position.BG2, -2.0); knightPV.put(Position.BG1, -3.5);
                knightPV.put(Position.BF4, 1.0); knightPV.put(Position.BF3, 0.8); knightPV.put(Position.BF2, 0.0); knightPV.put(Position.BF1, -3.0);
                knightPV.put(Position.BE4, 1.5); knightPV.put(Position.BE3, 0.8); knightPV.put(Position.BE2, 0.0); knightPV.put(Position.BE1, -3.0);
                knightPV.put(Position.BD4, 1.5); knightPV.put(Position.BD3, 0.8); knightPV.put(Position.BD2, 0.0); knightPV.put(Position.BD1, -3.0);
                knightPV.put(Position.BC4, 1.0); knightPV.put(Position.BC3, 0.8); knightPV.put(Position.BC2, 0.0); knightPV.put(Position.BC1, -3.0);
                knightPV.put(Position.BB4, 0.0); knightPV.put(Position.BB3, 0.8); knightPV.put(Position.BB2, -2.0); knightPV.put(Position.BB1, -3.5);
                knightPV.put(Position.BA4, -3.0); knightPV.put(Position.BA3, -3.0); knightPV.put(Position.BA2, -3.5); knightPV.put(Position.BA1, -3.7);

                knightPV.put(Position.RH4, -3.0); knightPV.put(Position.RH3, -3.0); knightPV.put(Position.RH2, -3.5); knightPV.put(Position.RH1, -3.7);
                knightPV.put(Position.RG4, 0.8); knightPV.put(Position.RG3, 0.0); knightPV.put(Position.RG2, -2.0); knightPV.put(Position.RG1, -3.5);
                knightPV.put(Position.RF4, 1.0); knightPV.put(Position.RF3, 0.8); knightPV.put(Position.RF2, 0.0); knightPV.put(Position.RF1, -3.0);
                knightPV.put(Position.RE4, 1.5); knightPV.put(Position.RE3, 0.8); knightPV.put(Position.RE2, 0.0); knightPV.put(Position.RE1, -3.0);
                knightPV.put(Position.RD4, 1.5); knightPV.put(Position.RD3, 0.8); knightPV.put(Position.RD2, 0.0); knightPV.put(Position.RD1, -3.0);
                knightPV.put(Position.RC4, 1.0); knightPV.put(Position.RC3, 0.8); knightPV.put(Position.RC2, 0.0); knightPV.put(Position.RC1, -3.0);
                knightPV.put(Position.RB4, 0.0); knightPV.put(Position.RB3, 0.8); knightPV.put(Position.RB2, -2.0); knightPV.put(Position.RB1, -3.5);
                knightPV.put(Position.RA4, -3.0); knightPV.put(Position.RA3, -3.0); knightPV.put(Position.RA2, -3.5); knightPV.put(Position.RA1, -3.7);


                bishopPV.put(Position.GA1, -2.0); bishopPV.put(Position.GA2, -1.0); bishopPV.put(Position.GA3, -1.0); bishopPV.put(Position.GA4, -1.0);
                bishopPV.put(Position.GB1, -1.0); bishopPV.put(Position.GB2, 0.5); bishopPV.put(Position.GB3, 0.8); bishopPV.put(Position.GB4, 0.0);
                bishopPV.put(Position.GC1, -1.0); bishopPV.put(Position.GC2, 0.0); bishopPV.put(Position.GC3, 0.8); bishopPV.put(Position.GC4, 0.8);
                bishopPV.put(Position.GD1, -1.0); bishopPV.put(Position.GD2, 0.0); bishopPV.put(Position.GD3, 0.8); bishopPV.put(Position.GD4, 0.8);
                bishopPV.put(Position.GE1, -1.0); bishopPV.put(Position.GE2, 0.0); bishopPV.put(Position.GE3, 0.8); bishopPV.put(Position.GE4, 0.8);
                bishopPV.put(Position.GF1, -1.0); bishopPV.put(Position.GF2, 0.0); bishopPV.put(Position.GF3, 0.8); bishopPV.put(Position.GF4, 0.8);
                bishopPV.put(Position.GG1, -1.0); bishopPV.put(Position.GG2, 0.5); bishopPV.put(Position.GG3, 0.8); bishopPV.put(Position.GG4, 0.0);
                bishopPV.put(Position.GH1, -2.0); bishopPV.put(Position.GH2, -1.5); bishopPV.put(Position.GH3, -1.0); bishopPV.put(Position.GH4, -1.0);

                bishopPV.put(Position.BH4, -1.0); bishopPV.put(Position.BH3, -1.0); bishopPV.put(Position.BH2, -1.5); bishopPV.put(Position.BH1, -2.0);
                bishopPV.put(Position.BG4, 0.0); bishopPV.put(Position.BG3, 0.8); bishopPV.put(Position.BG2, 0.5); bishopPV.put(Position.BG1, -1.0);
                bishopPV.put(Position.BF4, 0.8); bishopPV.put(Position.BF3, 0.8); bishopPV.put(Position.BF2, 0.0); bishopPV.put(Position.BF1, -1.0);
                bishopPV.put(Position.BE4, 0.8); bishopPV.put(Position.BE3, 0.8); bishopPV.put(Position.BE2, 0.0); bishopPV.put(Position.BE1, -1.0);
                bishopPV.put(Position.BD4, 0.8); bishopPV.put(Position.BD3, 0.8); bishopPV.put(Position.BD2, 0.0); bishopPV.put(Position.BD1, -1.0);
                bishopPV.put(Position.BC4, 0.8); bishopPV.put(Position.BC3, 0.8); bishopPV.put(Position.BC2, 0.0); bishopPV.put(Position.BC1, -1.0);
                bishopPV.put(Position.BB4, 0.0); bishopPV.put(Position.BB3, 0.8); bishopPV.put(Position.BB2, 0.5); bishopPV.put(Position.BB1, -1.0);
                bishopPV.put(Position.BA4, -1.0); bishopPV.put(Position.BA3, -1.0); bishopPV.put(Position.BA2, -1.5); bishopPV.put(Position.BA1, -2.0);

                bishopPV.put(Position.RH4, -1.0); bishopPV.put(Position.RH3, -1.0); bishopPV.put(Position.RH2, -1.5); bishopPV.put(Position.RH1, -2.0);
                bishopPV.put(Position.RG4, 0.0); bishopPV.put(Position.RG3, 0.8); bishopPV.put(Position.RG2, 0.5); bishopPV.put(Position.RG1, -1.0);
                bishopPV.put(Position.RF4, 0.8); bishopPV.put(Position.RF3, 0.8); bishopPV.put(Position.RF2, 0.0); bishopPV.put(Position.RF1, -1.0);
                bishopPV.put(Position.RE4, 0.8); bishopPV.put(Position.RE3, 0.8); bishopPV.put(Position.RE2, 0.0); bishopPV.put(Position.RE1, -1.0);
                bishopPV.put(Position.RD4, 0.8); bishopPV.put(Position.RD3, 0.8); bishopPV.put(Position.RD2, 0.0); bishopPV.put(Position.RD1, -1.0);
                bishopPV.put(Position.RC4, 0.8); bishopPV.put(Position.RC3, 0.8); bishopPV.put(Position.RC2, 0.0); bishopPV.put(Position.RC1, -1.0);
                bishopPV.put(Position.RB4, 0.0); bishopPV.put(Position.RB3, 0.8); bishopPV.put(Position.RB2, 0.5); bishopPV.put(Position.RB1, -1.0);
                bishopPV.put(Position.RA4, -1.0); bishopPV.put(Position.RA3, -1.0); bishopPV.put(Position.RA2, -1.5); bishopPV.put(Position.RA1, -2.0);


                rookPV.put(Position.GA1, 0.0); rookPV.put(Position.GA2, -0.5); rookPV.put(Position.GA3, -0.5); rookPV.put(Position.GA4, -0.5);
                rookPV.put(Position.GB1, 0.0); rookPV.put(Position.GB2, 0.0); rookPV.put(Position.GB3, 0.0); rookPV.put(Position.GB4, 0.0);
                rookPV.put(Position.GC1, 0.0); rookPV.put(Position.GC2, 0.0); rookPV.put(Position.GC3, 0.0); rookPV.put(Position.GC4, 0.0);
                rookPV.put(Position.GD1, 0.5); rookPV.put(Position.GD2, 0.0); rookPV.put(Position.GD3, 0.0); rookPV.put(Position.GD4, 0.0);
                rookPV.put(Position.GE1, 0.5); rookPV.put(Position.GE2, 0.0); rookPV.put(Position.GE3, 0.0); rookPV.put(Position.GE4, 0.0);
                rookPV.put(Position.GF1, 0.0); rookPV.put(Position.GF2, 0.0); rookPV.put(Position.GF3, 0.0); rookPV.put(Position.GF4, 0.0);
                rookPV.put(Position.GG1, 0.0); rookPV.put(Position.GG2, 0.0); rookPV.put(Position.GG3, 0.0); rookPV.put(Position.GG4, 0.0);
                rookPV.put(Position.GH1, -0.0); rookPV.put(Position.GH2, -0.5); rookPV.put(Position.GH3, -0.5); rookPV.put(Position.GH4, -0.5);

                rookPV.put(Position.BH4, -0.5); rookPV.put(Position.BH3, -0.5); rookPV.put(Position.BH2, 0.5); rookPV.put(Position.BH1, 0.0);
                rookPV.put(Position.BG4, 0.0); rookPV.put(Position.BG3, 0.0); rookPV.put(Position.BG2, 1.0); rookPV.put(Position.BG1, 0.0);
                rookPV.put(Position.BF4, 0.0); rookPV.put(Position.BF3, 0.0); rookPV.put(Position.BF2, 1.0); rookPV.put(Position.BF1, 0.0);
                rookPV.put(Position.BE4, 0.0); rookPV.put(Position.BE3, 0.0); rookPV.put(Position.BE2, 1.0); rookPV.put(Position.BE1, 0.0);
                rookPV.put(Position.BD4, 0.0); rookPV.put(Position.BD3, 0.0); rookPV.put(Position.BD2, 1.0); rookPV.put(Position.BD1, 0.0);
                rookPV.put(Position.BC4, 0.0); rookPV.put(Position.BC3, 0.0); rookPV.put(Position.BC2, 1.0); rookPV.put(Position.BC1, 0.0);
                rookPV.put(Position.BB4, 0.0); rookPV.put(Position.BB3, 0.0); rookPV.put(Position.BB2, 1.0); rookPV.put(Position.BB1, 0.0);
                rookPV.put(Position.BA4, -0.5); rookPV.put(Position.BA3, -0.5); rookPV.put(Position.BA2, 0.5); rookPV.put(Position.BA1, 0.0);

                rookPV.put(Position.RH4, -0.5); rookPV.put(Position.RH3, -0.5); rookPV.put(Position.RH2, 0.5); rookPV.put(Position.RH1, 0.0);
                rookPV.put(Position.RG4, 0.0); rookPV.put(Position.RG3, 0.0); rookPV.put(Position.RG2, 1.0); rookPV.put(Position.RG1, 0.0);
                rookPV.put(Position.RF4, 0.0); rookPV.put(Position.RF3, 0.0); rookPV.put(Position.RF2, 1.0); rookPV.put(Position.RF1, 0.0);
                rookPV.put(Position.RE4, 0.0); rookPV.put(Position.RE3, 0.0); rookPV.put(Position.RE2, 1.0); rookPV.put(Position.RE1, 0.0);
                rookPV.put(Position.RD4, 0.0); rookPV.put(Position.RD3, 0.0); rookPV.put(Position.RD2, 1.0); rookPV.put(Position.RD1, 0.0);
                rookPV.put(Position.RC4, 0.0); rookPV.put(Position.RC3, 0.0); rookPV.put(Position.RC2, 1.0); rookPV.put(Position.RC1, 0.0);
                rookPV.put(Position.RB4, 0.0); rookPV.put(Position.RB3, 0.0); rookPV.put(Position.RB2, 1.0); rookPV.put(Position.RB1, 0.0);
                rookPV.put(Position.RA4, -0.5); rookPV.put(Position.RA3, -0.5); rookPV.put(Position.RA2, 0.5); rookPV.put(Position.RA1, 0.0);


                queenPV.put(Position.GA1, -1.8); queenPV.put(Position.GA2, -0.9); queenPV.put(Position.GA3, -0.9); queenPV.put(Position.GA4, 0.0);
                queenPV.put(Position.GB1, -0.9); queenPV.put(Position.GB2, 0.0); queenPV.put(Position.GB3, 0.6); queenPV.put(Position.GB4, 0.0);
                queenPV.put(Position.GC1, -0.9); queenPV.put(Position.GC2, 0.6); queenPV.put(Position.GC3, 0.6); queenPV.put(Position.GC4, 0.6);
                queenPV.put(Position.GD1, -0.6); queenPV.put(Position.GD2, 0.6); queenPV.put(Position.GD3, 0.6); queenPV.put(Position.GD4, 0.6);
                queenPV.put(Position.GE1, -0.6); queenPV.put(Position.GE2, 0.6); queenPV.put(Position.GE3, 0.6); queenPV.put(Position.GE4, 0.6);
                queenPV.put(Position.GF1, -0.9); queenPV.put(Position.GF2, 0.6); queenPV.put(Position.GF3, 0.6); queenPV.put(Position.GF4, 0.6);
                queenPV.put(Position.GG1, -0.9); queenPV.put(Position.GG2, 0.0); queenPV.put(Position.GG3, 0.6); queenPV.put(Position.GG4, 0.0);
                queenPV.put(Position.GH1, -1.8); queenPV.put(Position.GH2, -0.9); queenPV.put(Position.GH3, -0.9); queenPV.put(Position.GH4, 0.0);

                queenPV.put(Position.BH4, -0.6); queenPV.put(Position.BH3, -0.9); queenPV.put(Position.BH2, -0.9); queenPV.put(Position.BH1, -1.8);
                queenPV.put(Position.BG4, 0.6); queenPV.put(Position.BG3, 0.0); queenPV.put(Position.BG2, 0.0); queenPV.put(Position.BG1, -0.9);
                queenPV.put(Position.BF4, 0.6); queenPV.put(Position.BF3, 0.6); queenPV.put(Position.BF2, 0.0); queenPV.put(Position.BF1, -0.9);
                queenPV.put(Position.BE4, 0.6); queenPV.put(Position.BE3, 0.6); queenPV.put(Position.BE2, 0.0); queenPV.put(Position.BE1, -0.6);
                queenPV.put(Position.BD4, 0.6); queenPV.put(Position.BD3, 0.6); queenPV.put(Position.BD2, 0.0); queenPV.put(Position.BD1, -0.6);
                queenPV.put(Position.BC4, 0.6); queenPV.put(Position.BC3, 0.6); queenPV.put(Position.BC2, 0.0); queenPV.put(Position.BC1, -0.6);
                queenPV.put(Position.BB4, 0.6); queenPV.put(Position.BB3, 0.0); queenPV.put(Position.BB2, 0.0); queenPV.put(Position.BB1, -0.9);
                queenPV.put(Position.BA4, -0.6); queenPV.put(Position.BA3, -0.9); queenPV.put(Position.BA2, -0.9); queenPV.put(Position.BA1, -1.8);

                queenPV.put(Position.RH4, -0.6); queenPV.put(Position.RH3, -0.9); queenPV.put(Position.RH2, -0.9); queenPV.put(Position.RH1, -1.8);
                queenPV.put(Position.RG4, 0.6); queenPV.put(Position.RG3, 0.0); queenPV.put(Position.RG2, 0.0); queenPV.put(Position.RG1, -0.9);
                queenPV.put(Position.RF4, 0.6); queenPV.put(Position.RF3, 0.6); queenPV.put(Position.RF2, 0.0); queenPV.put(Position.RF1, -0.9);
                queenPV.put(Position.RE4, 0.6); queenPV.put(Position.RE3, 0.6); queenPV.put(Position.RE2, 0.0); queenPV.put(Position.RE1, -0.6);
                queenPV.put(Position.RD4, 0.6); queenPV.put(Position.RD3, 0.6); queenPV.put(Position.RD2, 0.0); queenPV.put(Position.RD1, -0.6);
                queenPV.put(Position.RC4, 0.6); queenPV.put(Position.RC3, 0.6); queenPV.put(Position.RC2, 0.0); queenPV.put(Position.RC1, -0.6);
                queenPV.put(Position.RB4, 0.6); queenPV.put(Position.RB3, 0.0); queenPV.put(Position.RB2, 0.0); queenPV.put(Position.RB1, -0.9);
                queenPV.put(Position.RA4, -0.6); queenPV.put(Position.RA3, -0.9); queenPV.put(Position.RA2, -0.9); queenPV.put(Position.RA1, -1.8);


                kingPV.put(Position.GA1, 2.0); kingPV.put(Position.GA2, 2.0); kingPV.put(Position.GA3, -1.0); kingPV.put(Position.GA4, -2.0);
                kingPV.put(Position.GB1, 3.0); kingPV.put(Position.GB2, 2.0); kingPV.put(Position.GB3, -2.0); kingPV.put(Position.GB4, -3.0);
                kingPV.put(Position.GC1, 1.0); kingPV.put(Position.GC2, 0.0); kingPV.put(Position.GC3, -2.0); kingPV.put(Position.GC4, -3.0);
                kingPV.put(Position.GD1, 0.0); kingPV.put(Position.GD2, 0.0); kingPV.put(Position.GD3, -2.0); kingPV.put(Position.GD4, -4.0);
                kingPV.put(Position.GE1, 0.0); kingPV.put(Position.GE2, 0.0); kingPV.put(Position.GE3, -2.0); kingPV.put(Position.GE4, -4.0);
                kingPV.put(Position.GF1, 1.0); kingPV.put(Position.GF2, 0.0); kingPV.put(Position.GF3, -2.0); kingPV.put(Position.GF4, -3.0);
                kingPV.put(Position.GG1, 3.0); kingPV.put(Position.GG2, 2.0); kingPV.put(Position.GG3, -2.0); kingPV.put(Position.GG4, -3.0);
                kingPV.put(Position.GH1, 2.0); kingPV.put(Position.GH2, 2.0); kingPV.put(Position.GH3, -1.0); kingPV.put(Position.GH4, -2.0);

                kingPV.put(Position.BH4, -3.0); kingPV.put(Position.BH3, -3.0); kingPV.put(Position.BH2, -3.0); kingPV.put(Position.BH1, -3.0);
                kingPV.put(Position.BG4, -4.0); kingPV.put(Position.BG3, -4.0); kingPV.put(Position.BG2, -4.0); kingPV.put(Position.BG1, -4.0);
                kingPV.put(Position.BF4, -4.0); kingPV.put(Position.BF3, -4.0); kingPV.put(Position.BF2, -4.0); kingPV.put(Position.BF1, -4.0);
                kingPV.put(Position.BE4, -5.0); kingPV.put(Position.BE3, -5.0); kingPV.put(Position.BE2, -5.0); kingPV.put(Position.BE1, -5.0);
                kingPV.put(Position.BD4, -5.0); kingPV.put(Position.BD3, -5.0); kingPV.put(Position.BD2, -5.0); kingPV.put(Position.BD1, -5.0);
                kingPV.put(Position.BC4, -4.0); kingPV.put(Position.BC3, -4.0); kingPV.put(Position.BC2, -4.0); kingPV.put(Position.BC1, -4.0);
                kingPV.put(Position.BB4, -4.0); kingPV.put(Position.BB3, -4.0); kingPV.put(Position.BB2, -4.0); kingPV.put(Position.BB1, -4.0);
                kingPV.put(Position.BA4, -3.0); kingPV.put(Position.BA3, -3.0); kingPV.put(Position.BA2, -3.0); kingPV.put(Position.BA1, -3.0);

                kingPV.put(Position.RH4, -3.0); kingPV.put(Position.RH3, -3.0); kingPV.put(Position.RH2, -3.0); kingPV.put(Position.RH1, -3.0);
                kingPV.put(Position.RG4, -4.0); kingPV.put(Position.RG3, -4.0); kingPV.put(Position.RG2, -4.0); kingPV.put(Position.RG1, -4.0);
                kingPV.put(Position.RF4, -4.0); kingPV.put(Position.RF3, -4.0); kingPV.put(Position.RF2, -4.0); kingPV.put(Position.RF1, -4.0);
                kingPV.put(Position.RE4, -5.0); kingPV.put(Position.RE3, -5.0); kingPV.put(Position.RE2, -5.0); kingPV.put(Position.RE1, -5.0);
                kingPV.put(Position.RD4, -5.0); kingPV.put(Position.RD3, -5.0); kingPV.put(Position.RD2, -5.0); kingPV.put(Position.RD1, -5.0);
                kingPV.put(Position.RC4, -4.0); kingPV.put(Position.RC3, -4.0); kingPV.put(Position.RC2, -4.0); kingPV.put(Position.RC1, -4.0);
                kingPV.put(Position.RB4, -4.0); kingPV.put(Position.RB3, -4.0); kingPV.put(Position.RB2, -4.0); kingPV.put(Position.RB1, -4.0);
                kingPV.put(Position.RA4, -3.0); kingPV.put(Position.RA3, -3.0); kingPV.put(Position.RA2, -3.0); kingPV.put(Position.RA1, -3.0);

                break;

            default:
                System.out.print("If you are seeing this, something is wrong. No color was given.");
                break;
        }
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
                        possibleMoves.add(new Position[] { position, p }); // move is legal, add it to the list
                    }
                } catch (ImpossiblePositionException e) {
                    break; // ended up in an illegal position, abandon that step and try another
                }

            }
        }
        return possibleMoves;
    }

    /**
     * returns a HashMap of the position of every piece the current player has on
     * the board mapped to a set of all possible moves
     * 
     * @param board The current board state
     * @return a HashMap mapping every pieces position to a set of valid moves
     */
    /* private HashMap<Position, HashSet<Position[]>> getAllAvailableMoves(Board boardState, Colour player) {
        HashMap<Position, HashSet<Position[]>> allMoves = new HashMap<Position, HashSet<Position[]>>();
        for (Position pos : boardState.getPositions(player)) {
            HashSet<Position[]> moves = getAvailableMoves(boardState, pos);
            if(!moves.isEmpty()) {
                allMoves.put(pos, moves);
            }
        }
        return allMoves;
    } */

    private HashSet<Position[]> getAllAvailableMoves(Board boardState, Colour player) {
        HashSet<Position[]> allMoves = new HashSet<Position[]>();
        HashSet<Position> piecePosSet = (HashSet<Position>) boardState.getPositions(player);
        for(Position pos : piecePosSet) {
            HashSet<Position[]> availMoves = getAvailableMoves(boardState, pos);
            if(!availMoves.isEmpty())
                allMoves.addAll(availMoves);
        }
        return allMoves;
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
     * Determines whether or not we should choose an exploration or explotative next
     * step. Starts by favouring exploring during the early stages then transitions
     * to exploiting later on in the game
     * 
     * @return true if we should choose exploration, otherwise false
     */
    private boolean shouldExplore() {
        if (random.nextDouble() < epsilon) {
            epsilon *= 0.95;
            return true;
        } else {
            epsilon *= 0.95;
            return false;
        }
    }

    /**
     * This function takes in a board, finds all available moves the agent can make
     * at that point in time and examines all of the state-action pairings, adding
     * any new ones it finds to the q-Table and setting their values to be equal to
     * 0. For each examined state-action pairing it will check it's Q-Table Value.
     * Once examination is done it returns the highest q-Table Value found.
     * 
     * @param boardState the state of the board wishing to be examined
     * @return the highest q-Table value found from the given board state
     */
    private double argMaxQ(Board boardState) {
        HashSet<Position[]> availMoves = getAllAvailableMoves(boardState, myColour);

        double maxEstUtility = Double.MIN_VALUE;

        for (Position[] action : availMoves) {
            StateAction curExaminedSA = new StateAction(boardState, action);
            if (nTimesExecuted.containsKey(curExaminedSA)) { // If we have seen the state-action pair already use
                                                             // its existing q-value
                if (qTable.get(curExaminedSA) > maxEstUtility) {
                    maxEstUtility = qTable.get(curExaminedSA);
                }
            } else { // Add the new state-action pair to the q-table and set its value to 0
                qTable.put(curExaminedSA, 0.0);
                nTimesExecuted.put(curExaminedSA, 0);
                if (qTable.get(curExaminedSA) > maxEstUtility) {
                    maxEstUtility = 0.0;
                }
            }
        }

        return maxEstUtility;

    }

    /**
     * The update function for the QLearningAgent. Uses temporal-difference learning
     * (TDL).
     */
    private void update() {
        if (curBoardState.gameOver()) { // If the game is over add this state to the table if it does not already exist
            qTable.putIfAbsent(new StateAction(curBoardState, null), curReward);
        }
        if (prevBoardState != null) { // If we have already seen a previous state
            StateAction curSA = new StateAction(prevBoardState, myLastAction);
            double learningRate; // The learning rate to be used in the update function
            double curQValue; // The current Q value of curSA
            if (nTimesExecuted.putIfAbsent(curSA, 1) != null) { // If curSA is in nTimesExecuted update the times its been executed by one
                int nTimesSeen = nTimesExecuted.get(curSA) + 1;
                nTimesExecuted.put(curSA, nTimesSeen);
                learningRate = getLearningRate(nTimesSeen);
                curQValue = qTable.get(curSA);
                qTable.put(curSA, (curQValue + (learningRate * (prevReward + argMaxQ(curBoardState) - curQValue))));
            } else { // Add curSA to the q-table
                learningRate = getLearningRate(1);
                qTable.put(curSA, (0.0 + (learningRate) * (prevReward + argMaxQ(curBoardState) - 0.0)));
            }

        }
    }

    /**
     * Checks to see if we are currently under check and returns the negative value
     * of it if we are. More weight given to those who can put us in check and move
     * next turn.
     * 
     * @param boardState the board in which we will exmaine
     * @return negative value if under check, otherwise 0.0
     */
    private double amUnderCheck(Board boardState) {
        double soln = 0.0;
        Position kingPos = null;
        HashSet<Position[]> availMoves = new HashSet<>();
        for(Position pos : boardState.getPositions(myColour)) {
            if(boardState.getPiece(pos).getType() == PieceType.KING) {
                kingPos = pos;
                break;
            }
        }
        switch (myColour) {
            case BLUE:
                availMoves = getAllAvailableMoves(boardState, Colour.GREEN);
                for (Position[] action : availMoves) {
                    if (action[1].equals(kingPos)) {
                        soln -= 100.0;
                    }
                }
                availMoves = getAllAvailableMoves(boardState, Colour.RED);
                for (Position[] action : availMoves) {
                    if (action[1].equals(kingPos)) {
                        soln -= 35.0;
                    }
                }
                break;

            case GREEN:
                availMoves = getAllAvailableMoves(boardState, Colour.RED);
                for (Position[] action : availMoves) {
                    if (action[1].equals(kingPos)) {
                        soln -= 100.0;
                    }
                }
                availMoves = getAllAvailableMoves(boardState, Colour.BLUE);
                for (Position[] action : availMoves) {
                    if (action[1].equals(kingPos)) {
                        soln -= 35.0;
                    }
                }
                break;

            case RED:
                availMoves = getAllAvailableMoves(boardState, Colour.BLUE);
                for (Position[] action : availMoves) {
                    if (action[1].equals(kingPos)) {
                        soln -= 100.0;
                    }
                }
                availMoves = getAllAvailableMoves(boardState, Colour.GREEN);
                for (Position[] action : availMoves) {
                    if (action[1].equals(kingPos)) {
                        soln -= 35.0;
                    }
                }
                break;
        
            default:
                break;
        }
        return soln;
    }
    
    /**
     * This function checks where every single one of the agent's pieces are on the
     * board and sums up the value each piece has in its current position
     * 
     * @param boardState the board state in which to examine
     * @return the cumulative positioning value of all the agent's pieces on the
     *         board
     */
    private double getPiecePositionValue(Board boardState) {
        HashSet<Position> positions = (HashSet<Position>) boardState.getPositions(myColour);
        double val = 0.0;

        for (Position pos : positions) {
            switch (boardState.getPiece(pos).getType()) {
                case PAWN:
                    val += pawnPV.get(pos);
                    break;
                
                case KNIGHT:
                    val += knightPV.get(pos);
                    break;

                case BISHOP:
                    val += bishopPV.get(pos);
                    break;

                case ROOK:
                    val += rookPV.get(pos);
                    break;

                case QUEEN:
                    val += queenPV.get(pos);
                    break;

                case KING:
                    val += kingPV.get(pos);
                    break;
            
                default:
                    break;
            }
        }

        return val;
    }

    private double calculateCurrentReward() {
        double curRewardValue = curBoardState.score(myColour) + getPiecePositionValue(curBoardState) + amUnderCheck(curBoardState);
        double soln = curRewardValue - prevRewardValue;
        prevRewardValue = curRewardValue;
        return soln;
    }

    private void executeAction(Position[] action) {
        /* System.out.printf("%s submits the action:", myColour.toString());
        System.out.printf(" %s -> ", action[0].toString());
        System.out.printf("%s\r\n", action[1].toString()); */
        myLastAction = action.clone();
        try {
            prevBoardState = (Board) curBoardState.clone(); 
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            System.out.println("Could not clone curBoardState");
        }
        prevReward = curReward;
    }

    private double quickUtilEstimate(Board board, Position[] action) {
        double soln = 0.0;
        try {
            Board estBoard = (Board) board.clone(); 
            estBoard.move(action[0], action[1], 0);
            soln += curReward;
            switch (board.getPiece(action[0]).getType()) {
                case PAWN:
                    soln -= pawnPV.get(action[0]);
                    soln += pawnPV.get(action[1]);
                    soln += amUnderCheck(estBoard);
                    break;
                
                case KNIGHT:
                    soln -= knightPV.get(action[0]);
                    soln += knightPV.get(action[1]);
                    soln += amUnderCheck(estBoard);
                    break;

                case BISHOP:
                    soln -= bishopPV.get(action[0]);
                    soln += bishopPV.get(action[1]);
                    soln += amUnderCheck(estBoard);
                    break;

                case ROOK:
                    soln -= rookPV.get(action[0]);
                    soln += rookPV.get(action[1]);
                    soln += amUnderCheck(estBoard);
                    break;

                case QUEEN:
                    soln -= queenPV.get(action[0]);
                    soln += queenPV.get(action[1]);
                    soln += amUnderCheck(estBoard);
                    break;

                case KING:
                    soln -= kingPV.get(action[0]);
                    soln += kingPV.get(action[1]);
                    soln += amUnderCheck(estBoard);
                    break;
            
                default:
                    break;
            }
            return soln;
        } catch (CloneNotSupportedException | ImpossiblePositionException | NullPointerException e) {
            e.printStackTrace();
            System.out.printf("A null action was passed to quickUtilEstimate.\n\r\n\r%s - %s\n\r\n\r", action[0].toString(), action[1].toString());
            /* System.out.println("Could not clone board"); */
            return soln;
        }
    }

    /**
     * Writes the qTable to the file qTableStorage and writes nTimesExecuted to the
     * file nTimesExecutedStorage
     */
    private void storeData() {
        /* return; */
        if(turnsPlayed % 3 != 0) {
            turnsPlayed++;
            return;
        }
        turnsPlayed++;
        switch (myColour) {
            case BLUE:
                try {
                    FileOutputStream qFileOut = new FileOutputStream(qTableStorageBlue);
                    ObjectOutputStream qObjectOut = new ObjectOutputStream(qFileOut);
                    qObjectOut.writeObject(qTable);
                    qFileOut.close();
                    qObjectOut.close();
    
                    FileOutputStream nFileOut = new FileOutputStream(nTimesExecutedStorageBlue);
                    ObjectOutputStream nObjectOut = new ObjectOutputStream(nFileOut);
                    nObjectOut.writeObject(nTimesExecuted);
                    nFileOut.close();
                    nObjectOut.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.print("Q Table storage or n Times Executed Storage could not be written to disk.");
                }
                break;

            case GREEN:
                try {
                    FileOutputStream qFileOut = new FileOutputStream(qTableStorageGreen);
                    ObjectOutputStream qObjectOut = new ObjectOutputStream(qFileOut);
                    qObjectOut.writeObject(qTable);
                    qFileOut.close();
                    qObjectOut.close();
    
                    FileOutputStream nFileOut = new FileOutputStream(nTimesExecutedStorageGreen);
                    ObjectOutputStream nObjectOut = new ObjectOutputStream(nFileOut);
                    nObjectOut.writeObject(nTimesExecuted);
                    nFileOut.close();
                    nObjectOut.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.print("Q Table storage or n Times Executed Storage could not be written to disk.");
                }
                break;

            case RED:
                try {
                    FileOutputStream qFileOut = new FileOutputStream(qTableStorageRed);
                    ObjectOutputStream qObjectOut = new ObjectOutputStream(qFileOut);
                    qObjectOut.writeObject(qTable);
                    qFileOut.close();
                    qObjectOut.close();
    
                    FileOutputStream nFileOut = new FileOutputStream(nTimesExecutedStorageRed);
                    ObjectOutputStream nObjectOut = new ObjectOutputStream(nFileOut);
                    nObjectOut.writeObject(nTimesExecuted);
                    nFileOut.close();
                    nObjectOut.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.print("Q Table storage or n Times Executed Storage could not be written to disk.");
                }
                break;
        
            default:
                break;
        }
    }

    /*------------------------------------------------------- Public Functions -------------------------------------------------------*/

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
        if (board.getMoveCount() < 2) {
            myColour = board.getTurn();
            init();
        }
        curBoardState = board;
        curReward = calculateCurrentReward();
        update();
        if (board.gameOver()) { // This should never happen... But just in case
            return null;
        }

        Position[] chosenAction = new Position[2];
        boolean set = false;

        HashSet<Position[]> availMoves = getAllAvailableMoves(board, myColour);

        if(shouldExplore()) { // If I should explore choose the state-action pair with the lowest visits, or if there is a state we haven't explored immediately choose that
            int lowestVisitedSA = Integer.MAX_VALUE;
            for (Position[] action : availMoves) {
                if(set == false) {
                    chosenAction[0] = action[0];
                    chosenAction[1] = action[1];
                    set = true;
                }
                if(action[0] == null || action[1] == null) {
                    System.out.println("One of the actions was null and we can't continue");
                    continue;
                } else if(!board.isLegalMove(action[0], action[1])) {
                    System.out.printf("A move we were going to examine is illegal... Move: %s -> %s\r\n\r\n", action[0].toString(), action[1].toString());
                    continue;
                }
                StateAction curExaminedSA = new StateAction(board, action);
                if (nTimesExecuted.containsKey(curExaminedSA)) { // We have seen the state-action pair already
                    if (nTimesExecuted.get(curExaminedSA) == 0) { // Proceed using this state-action
                        System.out.printf("The action pair trying to be passed to  `executeAction`: ");
                        System.out.printf("%s -> ", action[0].toString());
                        System.out.printf("%s\r\n", action[1].toString());
                        executeAction(action);
                        storeData();
                        return action.clone();
                    } else if (nTimesExecuted.get(curExaminedSA) <= lowestVisitedSA) {
                        lowestVisitedSA = nTimesExecuted.get(curExaminedSA);
                        chosenAction = action;
                    }
                } else { // Add the new state-action pair to the q-table and set its value to 0
                    qTable.put(curExaminedSA, 0.0);
                    nTimesExecuted.put(curExaminedSA, 0);
                    // Proceed using this state-action pair
                    System.out.printf("The action pair trying to be passed to  `executeAction`: ");
                    System.out.printf("%s -> ", action[0].toString());
                    System.out.printf("%s\r\n", action[1].toString());
                    executeAction(action);
                    storeData();
                    return action.clone();
                }
            }
            System.out.printf("The action pair trying to be passed to  `executeAction`: ");
            System.out.printf("%s -> ", chosenAction[0].toString());
            System.out.printf("%s\r\n", chosenAction[1].toString());
            executeAction(chosenAction);
            storeData();
            return chosenAction.clone();
        } else { // Just do it normally, be greedy and take the state-action pair that has the highest utility/value/reward
            double maxEstUtility = Double.MIN_VALUE;
                for (Position[] action : availMoves) {
                    if(set == false) {
                        chosenAction[0] = action[0];
                        chosenAction[1] = action[1];
                        set = true;
                    }
                    StateAction curExaminedSA = new StateAction(board, action);
                    if (nTimesExecuted.containsKey(curExaminedSA)) { // We have seen the state-action pair already set utility as its value
                        double estUtil = qTable.get(curExaminedSA);
                        if(estUtil == 0) {
                            estUtil = quickUtilEstimate(board, action);
                        }
                        if (estUtil >= maxEstUtility) { // Double check when more awake
                            maxEstUtility = estUtil;
                            chosenAction[0] = action[0];
                            chosenAction[1] = action[1];
                        }
                    } else { // Add the new state-action pair to the q-table and set its value to 0
                        qTable.put(curExaminedSA, 0.0);
                        nTimesExecuted.put(curExaminedSA, 0);
                        double estUtil = quickUtilEstimate(board, action);
                        if(estUtil >= maxEstUtility) { // check when more awake
                            maxEstUtility = estUtil;
                            chosenAction[0] = action[0];
                            chosenAction[1] = action[1];
                        }
                    }
                }
            System.out.printf("The action pair trying to be passed to  `executeAction`: ");
            System.out.printf("%s -> ", chosenAction[0].toString());
            System.out.printf("%s\r\n", chosenAction[1].toString());
            executeAction(chosenAction);
            storeData();
            return chosenAction.clone();
        }
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
