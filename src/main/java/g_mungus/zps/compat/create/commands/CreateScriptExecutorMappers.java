package g_mungus.zps.compat.create.commands;

public class CreateScriptExecutorMappers {
    public static void register(OnRegisterCreateCompatExecutorMappingsEvent event) {
        event.accept(
                new MappingEntry("create:mechanical_bearing", "set_movement_mode", "set_rotation_mode"),
                new MappingEntry("create:creative_motor", "set_generated_speed_in_rpm", "set_rpm"),
                new MappingEntry("create:rotation_speed_controller", "set_targeted_speed_in_rpm", "set_rpm"),
                new MappingEntry("create:secondary_linear_chassis", "set_range_of_sticky_sides", "set_sticky_range"),
                new MappingEntry("create:linear_chassis", "set_range_of_sticky_sides", "set_sticky_range"),
                new MappingEntry("create:radial_chassis", "set_range_of_sticky_sides", "set_sticky_range"),
                new MappingEntry("create:weighted_ejector", "set_ejected_stack_size", "set_stack_size"),
                new MappingEntry("create:clockwork_bearing", "set_clock_hand_arrangement", "set_clock_hand")
        );
    }
}
