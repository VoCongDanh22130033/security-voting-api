package com.nlu.authservice.service;

import com.nlu.authservice.entity.Department;
import com.nlu.authservice.entity.Employee;
import com.nlu.authservice.entity.Role;
import com.nlu.authservice.entity.User;
import com.nlu.authservice.entity.Voter;
import com.nlu.authservice.repository.DepartmentRepository;
import com.nlu.authservice.repository.EmployeeRepository;
import com.nlu.authservice.repository.RoleRepository;
import com.nlu.authservice.repository.UserRepository;
import com.nlu.authservice.repository.VoterRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EmployeeImportService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VoterRepository voterRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public List<String> importEmployees(MultipartFile file) throws Exception {
        List<String> errors = new ArrayList<>();
        List<Employee> employeesToSave = new ArrayList<>();

        // Cache departments to reduce DB queries
        Map<String, Department> departmentCache = departmentRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Department::getName, d -> d));

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            // Skip header row
            if (rows.hasNext()) {
                rows.next();
            }

            int rowNumber = 1;
            while (rows.hasNext()) {
                Row currentRow = rows.next();
                rowNumber++;

                Cell cellCode = currentRow.getCell(0);
                Cell cellName = currentRow.getCell(1);
                Cell cellEmail = currentRow.getCell(2);
                Cell cellDept = currentRow.getCell(3);

                String employeeCode = getCellValueAsString(cellCode);
                String fullName = getCellValueAsString(cellName);
                String email = getCellValueAsString(cellEmail);
                String departmentName = getCellValueAsString(cellDept);

                // Validation
                if (employeeCode.isEmpty() || fullName.isEmpty() || email.isEmpty() || departmentName.isEmpty()) {
                    errors.add("Lỗi ở dòng " + rowNumber + ": Dữ liệu bắt buộc không được để trống.");
                    continue;
                }

                if (employeeRepository.existsByEmployeeCode(employeeCode)) {
                    errors.add("Lỗi ở dòng " + rowNumber + ": Mã nhân viên '" + employeeCode + "' đã tồn tại.");
                    continue;
                }

                if (employeeRepository.existsByEmail(email)) {
                    errors.add("Lỗi ở dòng " + rowNumber + ": Email '" + email + "' đã tồn tại.");
                    continue;
                }

                if (userRepository.existsByEmail(email)) {
                    errors.add("Loi o dong " + rowNumber + ": Email '" + email + "' da ton tai trong bang tai khoan.");
                    continue;
                }

                Department department = departmentCache.get(departmentName);
                if (department == null) {
                    errors.add("Lỗi ở dòng " + rowNumber + ": Phòng ban '" + departmentName + "' không tồn tại trong hệ thống.");
                    continue;
                }

                Employee employee = new Employee();
                employee.setEmployeeCode(employeeCode);
                employee.setFullName(fullName);
                employee.setEmail(email);
                employee.setDepartment(department);
                employeesToSave.add(employee);
            }
        }

        if (errors.isEmpty()) {
            Role voterRole = roleRepository.findByName("ROLE_VOTER")
                    .orElseThrow(() -> new RuntimeException("Khong tim thay ROLE_VOTER."));

            for (Employee employee : employeesToSave) {
                Employee savedEmployee = employeeRepository.save(employee);

                User user = new User();
                user.setFullName(savedEmployee.getFullName());
                user.setEmail(savedEmployee.getEmail());
                user.setPassword(passwordEncoder.encode(savedEmployee.getEmployeeCode()));
                user.setVerified(true);
                user.setIsLock(0);
                user.setEmployee(savedEmployee);
                user.setRoles(Set.of(voterRole));
                User savedUser = userRepository.save(user);

                Voter voter = new Voter();
                voter.setUser(savedUser);
                voter.setFullName(savedUser.getFullName());
                voter.setVerified(true);
                voterRepository.save(voter);
            }
        }

        return errors;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                } else {
                    // Format as plain number without scientific notation
                    return String.format("%.0f", cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
}
