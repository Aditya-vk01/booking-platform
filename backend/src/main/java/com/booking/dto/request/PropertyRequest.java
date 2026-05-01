package com.booking.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

// Used for both POST /api/properties (create) and PUT /api/properties/{id} (update)
// The controller uses @Valid on this DTO for both operations
@Data
public class PropertyRequest {
  @NotBlank(message = "Property name is required")
  @Size(max = 255, message = "Property name cannot exceed 255 characters")
  // Example: "Luxury Chelsea Apartment"
  private String name;

  // No validation annotations = this field is optional
  // It can be null or empty - that is perfectly fine
  // A host might now want to write a description yet
  private String description;

  @NotBlank(message = "Location is required")
  // Full address: "123 Kings Road, Chelsea, London SW3 4PW"
  private String location;

  @NotBlank(message = "City is required")
  @Size(max = 100, message = "City name cannot exceed 100 characters")
  // Stored separately because search queries filter by city:
  //   WHERE city = 'London'
  // Having it in a separate indexed column is much faster than
  // searching inside the full location string
  private String city;

  @NotBlank(message = "Country is required")
  @Size(max = 100)
  private String country;

  // @NotNull (not @NotBlank) because BigDecimal is not a String
  // @NotBlank only works on Strings
  // @NotNull works on ANY type: BigDecimal, Integer, UUID, LocalDate, etc.
  @NotNull(message = "Price per night is required")
  // @DecimalMin validates the MINIMUM VALUE of a decimal number
  // "1.00" is a String here — Spring converts it to BigDecimal for comparison
  // 0.99 → fails (less than 1.00)
  // 1.00 → passes
  // 999.99 → passes
  @DecimalMin(value = "1.00", message = "Price must be at least $1.00")
  // @Digits validates the NUMBER OF DIGITS in a decimal
  // integer = 8  → max 8 digits before the decimal point (up to 99,999,999)
  // fraction = 2 → max 2 digits after the decimal point
  // 189.99  → valid
  // 189.999 → invalid (3 digits after decimal)
  // 1234567890.00 → invalid (10 digits before decimal)
  @Digits(integer = 8, fraction = 2, message = "Price format: up to 8 digits before decimal, 2 after")
  // BigDecimal, not double/float
  // This is CRITICAL for money calculations
  // double: 0.1 + 0.2 = 0.30000000000000004 (floating point error)
  // BigDecimal: 0.1 + 0.2 = 0.3 (exact)
  private BigDecimal pricePerNight;

  @NotNull(message = "Max guests is required")
  // @Min validates INTEGER minimum value
  // @Min(1) means the value must be >= 1
  // 0 → fails, 1 → passes
  @Min(value = 1, message = "Property must accommodate at least 1 guest")
  // @Max validates INTEGER maximum value
  // 50 → passes, 51 → fails
  @Max(value = 50, message = "Property cannot accomodate more than 50 guests")
  // Integer (uppercase) not int (lowercase)
  // int cannot be null — @NotNull wouldn't work on a primitive
  // Integer can be null — @NotNull catches that case
  private Integer maxGuests;
}
