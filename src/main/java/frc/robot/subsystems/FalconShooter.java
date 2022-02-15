// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.ctre.phoenix.motorcontrol.DemandType;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.RemoteSensorSource;
import com.ctre.phoenix.motorcontrol.StatusFrame;
import com.ctre.phoenix.motorcontrol.TalonFXControlMode;
import com.ctre.phoenix.motorcontrol.TalonFXFeedbackDevice;
import com.ctre.phoenix.motorcontrol.TalonFXInvertType;
import com.ctre.phoenix.motorcontrol.can.TalonFXConfiguration;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonFX;

import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.utils.Gains;

public class FalconShooter extends SubsystemBase {
    /** Hardware */
    WPI_TalonFX _leftMaster = new WPI_TalonFX(2, "rio");
    WPI_TalonFX _rightMaster = new WPI_TalonFX(1, "rio");

    // TODO Clean these out
    Joystick _gamepad = new Joystick(0);

    /** Latched values to detect on-press events for buttons and POV */
    boolean[] _btns = new boolean[kNumButtonsPlusOne];
    boolean[] btns = new boolean[kNumButtonsPlusOne];

    /** Invert Directions for Left and Right */
    TalonFXInvertType _leftInvert = TalonFXInvertType.CounterClockwise; // Same as invert = "false"
    TalonFXInvertType _rightInvert = TalonFXInvertType.Clockwise; // Same as invert = "true"

    /** Config Objects for motor controllers */
    TalonFXConfiguration _leftConfig = new TalonFXConfiguration();
    TalonFXConfiguration _rightConfig = new TalonFXConfiguration();

    /** Tracking variables */
    boolean _firstCall = false;
    boolean _state = false;

    /** Constants */
    public final static int kNumButtonsPlusOne = 10;

    /**
     * How many sensor units per rotation.
     * Using Talon FX Integrated Sensor.
     * 
     * @link https://github.com/CrossTheRoadElec/Phoenix-Documentation#what-are-the-units-of-my-sensor
     */
    public final static int kSensorUnitsPerRotation = 2048;

    /**
     * Set to zero to skip waiting for confirmation.
     * Set to nonzero to wait and report to DS if action fails.
     */
    public final static int kTimeoutMs = 30;

    /**
     * Motor neutral dead-band, set to the minimum 0.1%.
     */
    public final static double kNeutralDeadband = 0.001;

    /**
     * PID Gains may have to be adjusted based on the responsiveness of control
     * loop.
     * kF: 1023 represents output value to Talon at 100%, 20660 represents Velocity
     * units at 100% output
     * Not all set of Gains are used in this project and may be removed as desired.
     * 
     * kP kI kD kF Iz PeakOut
     */
    public final static Gains kGains_Distanc = new Gains(0.1, 0.0, 0.0, 0.0, 100, 0.50);
    public final static Gains kGains_Turning = new Gains(0.10, 0.0, 0.0, 0.0, 200, 1.00);
    public final static Gains kGains_Velocit = new Gains(0.1, 0.001, 5, 1023.0 / 20660.0, 300, 1.00);
    public final static Gains kGains_MotProf = new Gains(1.0, 0.0, 0.0, 1023.0 / 20660.0, 400, 1.00);

    /** ---- Flat constants, you should not need to change these ---- */
    /*
     * We allow either a 0 or 1 when selecting an ordinal for remote devices [You
     * can have up to 2 devices assigned remotely to a talon/victor]
     */
    public final static int REMOTE_0 = 0;
    public final static int REMOTE_1 = 1;
    /*
     * We allow either a 0 or 1 when selecting a PID Index, where 0 is primary and 1
     * is auxiliary
     */
    public final static int PID_PRIMARY = 0;
    public final static int PID_TURN = 1;
    /*
     * Firmware currently supports slots [0, 3] and can be used for either PID Set
     */
    public final static int SLOT_0 = 0;
    public final static int SLOT_1 = 1;
    public final static int SLOT_2 = 2;
    public final static int SLOT_3 = 3;

    /* ---- Named slots, used to clarify code ---- */
    public final static int kSlot_Distanc = SLOT_0;
    public final static int kSlot_Turning = SLOT_1;
    public final static int kSlot_Velocit = SLOT_2;
    public final static int kSlot_MotProf = SLOT_3;

    public FalconShooter() {
        /* Disable all motors */
        _rightMaster.set(TalonFXControlMode.PercentOutput, 0);
        _leftMaster.set(TalonFXControlMode.PercentOutput, 0);

        /* Set neutral modes */
        _leftMaster.setNeutralMode(NeutralMode.Brake);
        _rightMaster.setNeutralMode(NeutralMode.Brake);

        /* Configure output */
        _leftMaster.setInverted(TalonFXInvertType.CounterClockwise);
        _rightMaster.setInverted(TalonFXInvertType.Clockwise);

        /*
         * Talon FX does not need sensor phase set for its integrated sensor
         * This is because it will always be correct if the selected feedback device is
         * integrated sensor (default value)
         * and the user calls getSelectedSensor* to get the sensor's position/velocity.
         * 
         * https://phoenix-documentation.readthedocs.io/en/latest/ch14_MCSensor.html#
         * sensor-phase
         */
        // _leftMaster.setSensorPhase(true);
        // _rightMaster.setSensorPhase(true);

        /** Feedback Sensor Configuration */

        /** Distance Configs */

        /* Configure the left Talon's selected sensor as integrated sensor */
        _leftConfig.primaryPID.selectedFeedbackSensor = TalonFXFeedbackDevice.IntegratedSensor.toFeedbackDevice();

        /*
         * Configure the Remote (Left) Talon's selected sensor as a remote sensor for
         * the right Talon
         */
        _rightConfig.remoteFilter0.remoteSensorDeviceID = _leftMaster.getDeviceID(); // Device ID of Remote Source
        _rightConfig.remoteFilter0.remoteSensorSource = RemoteSensorSource.TalonFX_SelectedSensor; // Remote Source Type

        /*
         * Now that the Left sensor can be used by the master Talon,
         * set up the Left (Aux) and Right (Master) distance into a single
         * Robot distance as the Master's Selected Sensor 0.
         */
        setRobotDistanceConfigs(_rightInvert, _rightConfig);

        /* FPID for Distance */
        _rightConfig.slot2.kF = kGains_Velocit.kF;
        _rightConfig.slot2.kP = kGains_Velocit.kP;
        _rightConfig.slot2.kI = kGains_Velocit.kI;
        _rightConfig.slot2.kD = kGains_Velocit.kD;
        _rightConfig.slot2.integralZone = kGains_Velocit.kIzone;
        _rightConfig.slot2.closedLoopPeakOutput = kGains_Velocit.kPeakOutput;

        /*
         * false means talon's local output is PID0 + PID1, and other side Talon is PID0
         * - PID1
         * This is typical when the master is the right Talon FX and using Pigeon true
         * means talon's local output is PID0 - PID1, and other side Talon is PID0 PID1
         * This is typical when the master is the left Talon FX and using Pigeon
         */
        _rightConfig.auxPIDPolarity = false;

        /* Config the neutral deadband. */
        _leftConfig.neutralDeadband = kNeutralDeadband;
        _rightConfig.neutralDeadband = kNeutralDeadband;

        /**
         * 1ms per loop. PID loop can be slowed down if need be.
         * For example,
         * - if sensor updates are too slow
         * - sensor deltas are very small per update, so derivative error never gets
         * large enough to be useful.
         * - sensor movement is very slow causing the derivative error to be near zero.
         */
        int closedLoopTimeMs = 1;
        _rightConfig.slot0.closedLoopPeriod = closedLoopTimeMs;
        _rightConfig.slot1.closedLoopPeriod = closedLoopTimeMs;
        _rightConfig.slot2.closedLoopPeriod = closedLoopTimeMs;
        _rightConfig.slot3.closedLoopPeriod = closedLoopTimeMs;

        /* Motion Magic Configs */
        _rightConfig.motionAcceleration = 2000; // (distance units per 100 ms) per second
        _rightConfig.motionCruiseVelocity = 2000; // distance units per 100 ms

        /* APPLY the config settings */
        _leftMaster.configAllSettings(_leftConfig);
        _rightMaster.configAllSettings(_rightConfig);

        /* Set status frame periods to ensure we don't have stale data */
        _rightMaster.setStatusFramePeriod(StatusFrame.Status_12_Feedback1, 20, kTimeoutMs);
        _rightMaster.setStatusFramePeriod(StatusFrame.Status_13_Base_PIDF0, 20, kTimeoutMs);
        _leftMaster.setStatusFramePeriod(StatusFrame.Status_2_Feedback0, 5, kTimeoutMs);

        /* Initialize */
        _firstCall = true;
        _state = false;
        zeroSensors();
    }

    @Override
    public void periodic() {
        /* Gamepad processing */
        double forward = -1 * _gamepad.getY();
        double turn = _gamepad.getX();
        forward = Deadband(forward);
        turn = Deadband(turn);

        if (_firstCall) {
            System.out.println("This is Velocity Closed Loop with an Arbitrary Feed Forward.");
            System.out.println("Travel [-500, 500] RPM while having the ability to add a FeedForward with joyX ");
            zeroSensors();

            /* Determine which slot affects which PID */
            _rightMaster.selectProfileSlot(kSlot_Velocit, PID_PRIMARY);
        }

        /* Calculate targets from gamepad inputs */
        double target_RPM = forward * 2000; // +- 2000 RPM
        double target_unitsPer100ms = target_RPM * kSensorUnitsPerRotation / 600.0; // RPM -> Native units
        double feedFwdTerm = turn * 0.10; // Percentage added to the close loop output

        /*
         * Configured for Velocity Closed Loop on Integrated Sensors' Sum and Arbitrary
         * FeedForward on joyX
         */
        _rightMaster.set(TalonFXControlMode.Velocity, target_unitsPer100ms, DemandType.ArbitraryFeedForward,
                feedFwdTerm);
        _leftMaster.follow(_rightMaster);

        /* Uncomment to view RPM in Driver Station */
        double actual_RPM = (_rightMaster.getSelectedSensorVelocity() / (double) kSensorUnitsPerRotation * 600f);
        System.out.println("Vel[RPM]: " + actual_RPM + " Pos: " + _rightMaster.getSelectedSensorPosition());

        _firstCall = false;
    }

    /* Zero all sensors on Talons */
    void zeroSensors() {
        _leftMaster.getSensorCollection().setIntegratedSensorPosition(0, kTimeoutMs);
        _rightMaster.getSensorCollection().setIntegratedSensorPosition(0, kTimeoutMs);
        System.out.println("[Integrated Sensors] All sensors are zeroed.\n");
    }

    /**
     * Determines if SensorSum or SensorDiff should be used
     * for combining left/right sensors into Robot Distance.
     * 
     * Assumes Aux Position is set as Remote Sensor 0.
     * 
     * configAllSettings must still be called on the master config
     * after this function modifies the config values.
     * 
     * @param masterInvertType Invert of the Master Talon
     * @param masterConfig     Configuration object to fill
     */
    void setRobotDistanceConfigs(TalonFXInvertType masterInvertType, TalonFXConfiguration masterConfig) {
        /**
         * Determine if we need a Sum or Difference.
         * 
         * The auxiliary Talon FX will always be positive
         * in the forward direction because it's a selected sensor
         * over the CAN bus.
         * 
         * The master's native integrated sensor may not always be positive when forward
         * because sensor phase is only applied to *Selected Sensors*, not native
         * sensor sources. And we need the native to be combined with the
         * aux (other side's) distance into a single robot distance.
         */

        /*
         * THIS FUNCTION should not need to be modified.
         * This setup will work regardless of whether the master
         * is on the Right or Left side since it only deals with
         * distance magnitude.
         */

        /* Check if we're inverted */
        if (masterInvertType == TalonFXInvertType.Clockwise) {
            /*
             * If master is inverted, that means the integrated sensor
             * will be negative in the forward direction.
             * If master is inverted, the final sum/diff result will also be inverted.
             * This is how Talon FX corrects the sensor phase when inverting
             * the motor direction. This inversion applies to the *Selected Sensor*,
             * not the native value.
             * Will a sensor sum or difference give us a positive total magnitude?
             * Remember the Master is one side of your drivetrain distance and
             * Auxiliary is the other side's distance.
             * Phase | Term 0 | Term 1 | Result
             * Sum: -((-)Master + (+)Aux )| NOT OK, will cancel each other out
             * Diff: -((-)Master - (+)Aux )| OK - This is what we want, magnitude will be
             * correct and positive.
             * Diff: -((+)Aux - (-)Master)| NOT OK, magnitude will be correct but negative
             */

            // Local Integrated Sensor
            masterConfig.diff0Term = TalonFXFeedbackDevice.IntegratedSensor.toFeedbackDevice();
            // Aux Selected Sensor
            masterConfig.diff1Term = TalonFXFeedbackDevice.RemoteSensor0.toFeedbackDevice();
            // Diff0 - Diff1
            masterConfig.primaryPID.selectedFeedbackSensor = TalonFXFeedbackDevice.SensorDifference.toFeedbackDevice();
        } else {
            /* Master is not inverted, both sides are positive so we can sum them. */

            // Aux Selected Sensor
            masterConfig.sum0Term = TalonFXFeedbackDevice.RemoteSensor0.toFeedbackDevice();
            // Local IntegratedSensor
            masterConfig.sum1Term = TalonFXFeedbackDevice.IntegratedSensor.toFeedbackDevice();
            // Sum + Sum1
            masterConfig.primaryPID.selectedFeedbackSensor = TalonFXFeedbackDevice.SensorSum.toFeedbackDevice();
        }

        /*
         * Since the Distance is the sum of the two sides, divide by 2 so the total
         * isn't double
         * the real-world value
         */
        masterConfig.primaryPID.selectedFeedbackCoefficient = 0.5;
    }

    /** Deadband 5 percent, used on the gamepad (To be added to Framework?) */
    double Deadband(double value) {
        /* Upper deadband */
        if (value >= +0.05)
            return value;

        /* Lower deadband */
        if (value <= -0.05)
            return value;

        /* Outside deadband */
        return 0;
    }
}
