package org.symphonykernel.plugins;

import com.microsoft.semantickernel.semanticfunctions.annotations.DefineKernelFunction;
import com.microsoft.semantickernel.semanticfunctions.annotations.KernelFunctionParameter;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SamplePlugin {
	@DefineKernelFunction(name = "bookTable", description = "Book a table at a restaurant", returnType = "string")
    public String bookTable(
        @KernelFunctionParameter(name = "restaurant", description = "The name of the restaurant") String restaurant,
        @KernelFunctionParameter(name = "partySize", description = "The number of people in the party", type = int.class, required = true) int partySize,
        @KernelFunctionParameter(name = "customerName", description = "The name of the customer", required = false) String customerName,    
        @KernelFunctionParameter(name = "customerPhone", description = "The phone of the customer", required = true) String customerPhone) {    

       

        return "Booking sucessfull for ";
    }

    @DefineKernelFunction(name = "listReservations", description = "List all reservations for a customer")
    public List<String> listReservations(
        @KernelFunctionParameter(name = "customerName", description = "The name of the customer") String customerName) {
      
        List<String> reservations = new ArrayList<>();
      
                String reservation = "Restaurant: a booked"      ;
                reservations.add(reservation);
            
        

        return reservations;
    }


    @DefineKernelFunction(name = "cancelReservation", description = "Cancel a reservation at a restaurant", returnType = "string")
    public String cancelReservation(
        @KernelFunctionParameter(name = "restaurant", description = "The name of the restaurant") String restaurant,
        @KernelFunctionParameter(name = "date", description = "The date of the reservation in UTC", type = OffsetDateTime.class) OffsetDateTime date,
        @KernelFunctionParameter(name = "customerName", description = "The name of the customer") String customerName) {

       
        return "Reservation cancelled";
    }
}