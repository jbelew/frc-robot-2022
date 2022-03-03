package frc.robot.commands;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.wpilibj2.command.PIDCommand;
import frc.robot.subsystems.Drivetrain;

public class TestTurnAngle extends PIDCommand {
    // Motor characterization
    private static double kS = 0.8975;
    private static double kV = 0.0026249;
    private static double kA = 8.3993E-05;

    // Motor PID values
    private static double kP = 0.1084;
    private static double kI = 0;
    private static double kD = 0.0039948;

    private static final double kTurnToleranceDeg = 5;
    private static final double kTurnRateToleranceDegPerS = 10; // degrees per second

    double angleTarget;

    public TestTurnAngle(double targetAngleDegrees, Drivetrain m_drive) {
        super(
                // The controller that the command will use
                new PIDController(kP, kI, kD),
                // Close loop on heading
                m_drive::getHeadingDouble,
                // Set reference to target
                targetAngleDegrees,
                // Pipe output to turn robot
                output -> m_drive.arcadeDrive(0, ((output + kS)/ 12)),
                // Require the drive
                m_drive);

        // Set the controller to be continuous (because it is an angle controller)
        getController().enableContinuousInput(-180, 180);
        // Set the controller tolerance - the delta tolerance ensures the robot is
        // stationary at the setpoint before it is considered as having reached the
        // reference
        getController().setTolerance(kTurnToleranceDeg, kTurnRateToleranceDegPerS);
    }

    @Override
    public boolean isFinished() {
        return getController().atSetpoint();
    }
}