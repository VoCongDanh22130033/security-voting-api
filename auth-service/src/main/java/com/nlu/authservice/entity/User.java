package com.nlu.authservice.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id; // BẮT BUỘC dùng jakarta.persistence.Id
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Set;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
@Entity
@Table(name = "users")
public class User {

  @Id // Chỉ giữ lại 1 cái này
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String username;
  private String password;
  private String email;
  private String phone;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id"), // Khớp với 1 cột ID duy nhất của User
      inverseJoinColumns = @JoinColumn(name = "role_id") // Khớp với 1 cột ID duy nhất của Role
  )
  private Set<Role> roles;


}