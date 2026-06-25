package com.enicilion.backend.auth.service;

import com.enicilion.backend.auth.entity.StaffPermission;
import com.enicilion.backend.auth.entity.User;
import com.enicilion.backend.auth.entity.UserRole;
import com.enicilion.backend.auth.repository.StaffPermissionRepository;
import com.enicilion.backend.auth.repository.UserRepository;
import com.enicilion.backend.common.exception.BadValidationException;
import com.enicilion.backend.common.exception.ResourceNotFoundException;
import com.enicilion.backend.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffPermissionServiceTest {

    @Mock
    private StaffPermissionRepository repository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StaffPermissionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCheckPermissionForAdmin() {
        User admin = User.builder().email("admin@test.com").role(UserRole.admin).build();
        // Admin should bypass and not query the repository at all
        assertDoesNotThrow(() -> service.checkPermission(admin, "TREASURY"));
        verify(repository, never()).existsByEmailAndFeature(anyString(), anyString());
    }

    @Test
    void testCheckPermissionForStaffWithPermission() {
        User staff = User.builder().email("staff@test.com").role(UserRole.staff).build();
        when(repository.existsByEmailAndFeature("staff@test.com", "TREASURY")).thenReturn(true);

        assertDoesNotThrow(() -> service.checkPermission(staff, "TREASURY"));
        verify(repository, times(1)).existsByEmailAndFeature("staff@test.com", "TREASURY");
    }

    @Test
    void testCheckPermissionForStaffWithoutPermission() {
        User staff = User.builder().email("staff@test.com").role(UserRole.staff).build();
        when(repository.existsByEmailAndFeature("staff@test.com", "TREASURY")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> service.checkPermission(staff, "TREASURY"));
    }

    @Test
    void testCheckPermissionForNormalUser() {
        User user = User.builder().email("user@test.com").role(UserRole.user).build();
        assertThrows(UnauthorizedException.class, () -> service.checkPermission(user, "TREASURY"));
    }

    @Test
    void testGrantPermissionSuccess() {
        when(repository.existsByEmailAndFeature("staff@test.com", "TREASURY")).thenReturn(false);
        StaffPermission expected = StaffPermission.builder().email("staff@test.com").feature("TREASURY").build();
        when(repository.save(any(StaffPermission.class))).thenReturn(expected);

        StaffPermission result = service.grantPermission("staff@test.com", "TREASURY");

        assertNotNull(result);
        assertEquals("staff@test.com", result.getEmail());
        assertEquals("TREASURY", result.getFeature());
        verify(repository, times(1)).save(any(StaffPermission.class));
    }

    @Test
    void testGrantPermissionDuplicate() {
        when(repository.existsByEmailAndFeature("staff@test.com", "TREASURY")).thenReturn(true);
        assertThrows(BadValidationException.class, () -> service.grantPermission("staff@test.com", "TREASURY"));
    }

    @Test
    void testRevokePermissionSuccess() {
        StaffPermission permission = StaffPermission.builder().email("staff@test.com").feature("TREASURY").build();
        when(repository.findByEmailAndFeature("staff@test.com", "TREASURY")).thenReturn(Optional.of(permission));

        assertDoesNotThrow(() -> service.revokePermission("staff@test.com", "TREASURY"));
        verify(repository, times(1)).delete(permission);
    }

    @Test
    void testRevokePermissionNotFound() {
        when(repository.findByEmailAndFeature("staff@test.com", "TREASURY")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.revokePermission("staff@test.com", "TREASURY"));
    }

    @Test
    void testGetAllPermissions() {
        List<StaffPermission> list = List.of(new StaffPermission());
        when(repository.findAll()).thenReturn(list);

        List<StaffPermission> result = service.getAllPermissions();
        assertEquals(list, result);
    }
}
