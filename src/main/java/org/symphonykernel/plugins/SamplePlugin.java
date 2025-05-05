package org.symphonykernel.plugins;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.semantickernel.semanticfunctions.annotations.DefineKernelFunction;
import com.microsoft.semantickernel.semanticfunctions.annotations.KernelFunctionParameter;

/**
 * SamplePlugin is a demonstration plugin for handling reservations.
 * It provides methods to book tables and list reservations.
 */
public class SamplePlugin {

    List<String> reservations;

    /**
     * Default constructor for SamplePlugin.
     */
    public SamplePlugin() {
        reservations = new ArrayList<>();
    }

    /**
     * Books a table based on the provided details.
     *
     * @param restaurant the name of the restaurant
     * @param partySize the number of people in the party
     * @param customerName the name of the customer
     * @param customerPhone the phone of the customer
     * @return a confirmation message
     */
    @DefineKernelFunction(name = "bookTable", description = "Book a table at a restaurant", returnType = "string")
    public String bookTable(
            @KernelFunctionParameter(name = "restaurant", description = "The name of the restaurant") String restaurant,
            @KernelFunctionParameter(name = "partySize", description = "The number of people in the party", type = int.class, required = true) int partySize,
            @KernelFunctionParameter(name = "customerName", description = "The name of the customer", required = false) String customerName,
            @KernelFunctionParameter(name = "customerPhone", description = "The phone of the customer", required = true) String customerPhone) {

        String reservation = "Restaurant: " + restaurant + " booked for " + customerName;
        reservations.add(reservation);

        return "Booking sucessfull for " + restaurant;
    }

    /**
     * Lists all reservations for the given customer.
     *
     * @param customerName the name of the customer
     * @return a list of reservations
     */
    @DefineKernelFunction(name = "listReservations", description = "List all reservations for a customer")
    public List<String> listReservations(
            @KernelFunctionParameter(name = "customerName", description = "The name of the customer") String customerName) {

        return reservations;
    }

}
