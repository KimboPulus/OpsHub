package pl.fortaco.opshub.iot.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.fortaco.opshub.iot.model.IoTMachine;
import pl.fortaco.opshub.iot.repository.IoTMachineRepository;

@Component
public class IoTSeeder implements CommandLineRunner {
    private final IoTMachineRepository machines;

    public IoTSeeder(IoTMachineRepository machines) {
        this.machines = machines;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seed("WLD-01", "Welding robot cell", "Welding", 38.0, 1.35);
        seed("PRS-02", "Hydraulic press", "Forming", 31.0, 1.7);
        seed("CNC-03", "CNC machining center", "Machining", 46.0, 1.55);
        seed("ASM-04", "Final assembly station", "Assembly", 52.0, 0.9);
    }

    private void seed(String code, String name, String line, double targetCycleTime, double idealEnergyPerUnit) {
        if (machines.existsByCode(code)) {
            return;
        }

        IoTMachine machine = new IoTMachine();
        machine.setCode(code);
        machine.setName(name);
        machine.setLine(line);
        machine.setTargetCycleTime(targetCycleTime);
        machine.setIdealEnergyPerUnit(idealEnergyPerUnit);
        machines.save(machine);
    }
}
