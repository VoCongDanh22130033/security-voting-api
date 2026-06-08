package com.nlu.authservice.controller;

import com.nlu.authservice.entity.Department;
import com.nlu.authservice.entity.Employee;
import com.nlu.authservice.repository.DepartmentRepository;
import com.nlu.authservice.repository.EmployeeRepository;
import com.nlu.authservice.service.EmployeeImportService;
import com.nlu.authservice.service.KafkaProducerService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

  @Autowired
  private EmployeeImportService employeeImportService;

  @Autowired
  private EmployeeRepository employeeRepository;

  @Autowired
  private DepartmentRepository departmentRepository;

  @Autowired
  private KafkaProducerService auditLogger;

  @PostMapping("/import")
  @PreAuthorize("hasRole('ROLE_ORGANIZER')")
  public ResponseEntity<?> importEmployees(
      @RequestParam("file") MultipartFile file,
      @RequestHeader(value = "X-User-Email", required = false) String actorEmail) {
    if (file.isEmpty()) {
      return ResponseEntity.badRequest().body("Vui long chon mot file de upload.");
    }

    String userEmail = actorEmail != null ? actorEmail : "unknown";
    try {
      List<String> errors = employeeImportService.importEmployees(file);
      if (errors.isEmpty()) {
        auditLogger.sendAuditEvent(userEmail, "EMPLOYEE_IMPORT_SUCCESS",
            "Imported employees from Excel file: " + file.getOriginalFilename());
        return ResponseEntity.ok("Import danh sach nhan vien thanh cong.");
      }

      auditLogger.sendAuditEvent(userEmail, "EMPLOYEE_IMPORT_FAILED",
          "Employee import failed with " + errors.size() + " validation errors");
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    } catch (Exception e) {
      auditLogger.sendAuditEvent(userEmail, "EMPLOYEE_IMPORT_FAILED",
          "Employee import failed: " + e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Loi import: " + e.getMessage());
    }
  }

  @GetMapping
  @PreAuthorize("hasRole('ROLE_ORGANIZER')")
  public ResponseEntity<List<Employee>> getAllEmployees() {
    return ResponseEntity.ok(employeeRepository.findAll());
  }

  @GetMapping("/departments")
  @PreAuthorize("hasRole('ROLE_ORGANIZER')")
  public ResponseEntity<List<Department>> getDepartments() {
    return ResponseEntity.ok(departmentRepository.findAll());
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ROLE_ORGANIZER')")
  public ResponseEntity<Employee> getEmployeeById(@PathVariable Long id) {
    return employeeRepository.findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
