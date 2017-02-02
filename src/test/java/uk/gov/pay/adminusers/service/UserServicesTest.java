package uk.gov.pay.adminusers.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.pay.adminusers.model.Link;
import uk.gov.pay.adminusers.model.Role;
import uk.gov.pay.adminusers.model.User;
import uk.gov.pay.adminusers.persistence.dao.RoleDao;
import uk.gov.pay.adminusers.persistence.dao.UserDao;
import uk.gov.pay.adminusers.persistence.entity.RoleEntity;
import uk.gov.pay.adminusers.persistence.entity.UserEntity;

import javax.persistence.RollbackException;
import javax.ws.rs.WebApplicationException;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.exparity.hamcrest.date.ZonedDateTimeMatchers.within;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.pay.adminusers.resources.UserResource.USERS_RESOURCE;
import static uk.gov.pay.adminusers.service.UserServices.CONSTRAINT_VIOLATION_MESSAGE;

public class UserServicesTest {

    private UserDao userDao;
    private RoleDao roleDao;
    private PasswordHasher passwordHasher;
    private UserServices userServices;
    private LinksBuilder linksBuilder;

    @Before
    public void before() throws Exception {
        userDao = mock(UserDao.class);
        roleDao = mock(RoleDao.class);
        passwordHasher = mock(PasswordHasher.class);
        linksBuilder = new LinksBuilder("http://localhost");
        int testLoginAttemptCap = 3;
        userServices = new UserServices(userDao, roleDao, passwordHasher, linksBuilder, testLoginAttemptCap);
    }

    @Test(expected = WebApplicationException.class)
    public void shouldError_ifRoleNameDoesNotExist() throws Exception {
        User user = aUser();
        String nonExistentRole = "nonExistentRole";
        when(roleDao.findByRoleName(nonExistentRole)).thenReturn(Optional.empty());
        userServices.createUser(user, nonExistentRole);
    }

    @Test(expected = WebApplicationException.class)
    public void shouldError_ifRoleNameConflicts() throws Exception {
        User user = aUser();
        Role role = Role.role(2, "admin", "admin role");

        when(roleDao.findByRoleName(role.getName())).thenReturn(Optional.of(new RoleEntity(role)));
        when(passwordHasher.hash("random-password")).thenReturn("the hashed random-password");
        doThrow(new RollbackException(CONSTRAINT_VIOLATION_MESSAGE)).when(userDao).persist(any(UserEntity.class));

        userServices.createUser(user, role.getName());
    }

    @Test(expected = WebApplicationException.class)
    public void shouldError_ifUnknownErrorThrownWhenSaving() throws Exception {
        User user = aUser();
        Role role = Role.role(2, "admin", "admin role");

        when(roleDao.findByRoleName(role.getName())).thenReturn(Optional.of(new RoleEntity(role)));
        when(passwordHasher.hash("random-password")).thenReturn("the hashed random-password");
        doThrow(new RuntimeException("unknown error")).when(userDao).persist(any(UserEntity.class));

        userServices.createUser(user, role.getName());
    }

    @Test
    public void shouldPersist_aUserSuccessfully() throws Exception {
        User user = aUser();
        Role role = Role.role(2, "admin", "admin role");

        when(roleDao.findByRoleName(role.getName())).thenReturn(Optional.of(new RoleEntity(role)));
        when(passwordHasher.hash("random-password")).thenReturn("the hashed random-password");
        doNothing().when(userDao).persist(any(UserEntity.class));

        User persistedUser = userServices.createUser(user, role.getName());
        Link selfLink = Link.from(Link.Rel.self, "GET", "http://localhost" + USERS_RESOURCE + "/random-name");

        assertThat(persistedUser.getUsername(), is(user.getUsername()));
        assertThat(persistedUser.getPassword(), is(not(user.getPassword())));
        assertThat(persistedUser.getEmail(), is(user.getEmail()));
        assertThat(persistedUser.getGatewayAccountId(), is(user.getGatewayAccountId()));
        assertThat(persistedUser.getTelephoneNumber(), is(user.getTelephoneNumber()));
        assertThat(persistedUser.getOtpKey(), is(user.getOtpKey()));
        assertThat(persistedUser.getRoles().size(), is(1));
        assertThat(persistedUser.getRoles().get(0), is(role));
        assertThat(persistedUser.getLinks().get(0), is(selfLink));
    }

    @Test
    public void shouldFindAUserByUserName() throws Exception {
        User user = aUser();

        Optional<UserEntity> userEntityOptional = Optional.of(UserEntity.from(user));
        when(userDao.findByUsername("random-name")).thenReturn(userEntityOptional);

        Optional<User> userOptional = userServices.findUser("random-name");
        assertTrue(userOptional.isPresent());

        assertThat(userOptional.get().getUsername(), is("random-name"));
    }

    @Test
    public void shouldReturnEmpty_WhenFindByUserName_ifNotFound() throws Exception {
        when(userDao.findByUsername("random-name")).thenReturn(Optional.empty());

        Optional<User> userOptional = userServices.findUser("random-name");
        assertFalse(userOptional.isPresent());
    }

    @Test
    public void shouldReturnUserAndResetLoginCount_ifAuthenticationSuccessful() throws Exception {
        User user = aUser();
        user.setLoginCount(2);

        UserEntity userEntity = UserEntity.from(user);
        userEntity.setPassword("hashed-password");
        when(passwordHasher.isEqual("random-password", "hashed-password")).thenReturn(true);
        when(userDao.findByUsername("random-name")).thenReturn(Optional.of(userEntity));
        ArgumentCaptor<UserEntity> argumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
        when(userDao.merge(argumentCaptor.capture())).thenReturn(mock(UserEntity.class));

        Optional<User> userOptional = userServices.authenticate("random-name", "random-password");
        assertTrue(userOptional.isPresent());

        User authenticatedUser = userOptional.get();
        assertThat(authenticatedUser.getUsername(), is("random-name"));
        assertThat(authenticatedUser.getLinks().size(), is(1));
        assertThat(argumentCaptor.getValue().getLoginCount(), is(0));
    }

    @Test
    public void shouldReturnEmptyAndIncrementLoginCount_ifAuthenticationFail() throws Exception {
        User user = aUser();
        user.setLoginCount(1);
        UserEntity userEntity = UserEntity.from(user);
        userEntity.setPassword("hashed-password");

        when(passwordHasher.isEqual("random-password", "hashed-password")).thenReturn(false);
        when(userDao.findByUsername("random-name")).thenReturn(Optional.of(UserEntity.from(user)));
        ArgumentCaptor<UserEntity> argumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
        when(userDao.merge(argumentCaptor.capture())).thenReturn(mock(UserEntity.class));

        Optional<User> userOptional = userServices.authenticate("random-name", "random-password");
        assertFalse(userOptional.isPresent());

        UserEntity savedUser = argumentCaptor.getValue();
        assertTrue(within(3, SECONDS, savedUser.getCreatedAt()).matches(savedUser.getUpdatedAt()));
        assertThat(savedUser.getLoginCount(), is(2));
        assertThat(savedUser.isDisabled(), is(false));
    }

    @Test
    public void shouldErrorAndLockUser_onTooManyAuthFailures() throws Exception {
        User user = aUser();
        user.setLoginCount(3);
        UserEntity userEntity = UserEntity.from(user);
        userEntity.setPassword("hashed-password");

        when(passwordHasher.isEqual("random-password", "hashed-password")).thenReturn(false);
        when(userDao.findByUsername("random-name")).thenReturn(Optional.of(UserEntity.from(user)));
        ArgumentCaptor<UserEntity> argumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
        when(userDao.merge(argumentCaptor.capture())).thenReturn(mock(UserEntity.class));

        try {
            userServices.authenticate("random-name", "random-password");
        } catch (WebApplicationException e) {
            assertThat(e.getResponse().getStatus(), is(401));
            UserEntity savedUser = argumentCaptor.getValue();
            assertTrue(within(3, SECONDS, savedUser.getCreatedAt()).matches(savedUser.getUpdatedAt()));
            assertThat(savedUser.getLoginCount(), is(4));
            assertThat(savedUser.isDisabled(), is(true));
        }

    }

    @Test
    public void shouldErrorLockedWhenDisabled_evenIfUsernamePasswordMatches() throws Exception {
        User user = aUser();
        user.setDisabled(true);

        UserEntity userEntity = UserEntity.from(user);
        userEntity.setPassword("hashed-password");
        when(passwordHasher.isEqual("random-password", "hashed-password")).thenReturn(true);
        when(userDao.findByUsername("random-name")).thenReturn(Optional.of(userEntity));

        try {
            userServices.authenticate("random-name", "random-password");
        } catch (WebApplicationException e) {
            assertThat(e.getResponse().getStatus(), is(401));
        }

    }

    @Test
    public void shouldIncreaseLoginCount_whenRecordLoginAttempt() throws Exception {
        User user = aUser();
        user.setLoginCount(1);

        Optional<UserEntity> userEntityOptional = Optional.of(UserEntity.from(user));
        when(userDao.findByUsername("random-name")).thenReturn(userEntityOptional);
        ArgumentCaptor<UserEntity> argumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
        when(userDao.merge(argumentCaptor.capture())).thenReturn(mock(UserEntity.class));

        Optional<User> userOptional = userServices.recordLoginAttempt("random-name");

        assertTrue(userOptional.isPresent());

        assertThat(userOptional.get().getUsername(), is("random-name"));
        assertThat(userOptional.get().getLoginCount(), is(2));
        assertThat(userOptional.get().isDisabled(), is(false));

        UserEntity savedUser = argumentCaptor.getValue();
        assertTrue(within(3, SECONDS, savedUser.getCreatedAt()).matches(savedUser.getUpdatedAt()));
    }

    @Test
    public void shouldLockAccount_whenRecordLoginAttempt_ifMoreThanAllowedLoginAttempts() throws Exception {
        User user = aUser();
        user.setLoginCount(3);

        Optional<UserEntity> userEntityOptional = Optional.of(UserEntity.from(user));
        when(userDao.findByUsername("random-name")).thenReturn(userEntityOptional);
        ArgumentCaptor<UserEntity> argumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
        when(userDao.merge(argumentCaptor.capture())).thenReturn(mock(UserEntity.class));

        try {
            userServices.recordLoginAttempt("random-name");
        } catch (WebApplicationException ex) {
            UserEntity savedUser = argumentCaptor.getValue();
            assertTrue(within(3, SECONDS, savedUser.getCreatedAt()).matches(savedUser.getUpdatedAt()));
            assertThat(savedUser.getLoginCount(), is(4));
            assertThat(savedUser.isDisabled(), is(true));
        }

    }

    @Test
    public void shouldReturnEmpty_whenRecordLoginAttempt_ifUserNotFound() throws Exception {
        when(userDao.findByUsername("random-name")).thenReturn(Optional.empty());
        Optional<User> userOptional = userServices.recordLoginAttempt("random-name");

        assertFalse(userOptional.isPresent());
    }

    @Test
    public void shouldReturnUser_whenResetLoginAttempt_ifUserFound() throws Exception {
        User user = aUser();

        Optional<UserEntity> userEntityOptional = Optional.of(UserEntity.from(user));
        when(userDao.findByUsername("random-name")).thenReturn(userEntityOptional);
        ArgumentCaptor<UserEntity> argumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
        when(userDao.merge(argumentCaptor.capture())).thenReturn(mock(UserEntity.class));

        Optional<User> userOptional = userServices.resetLoginAttempts("random-name");
        assertTrue(userOptional.isPresent());

        assertThat(userOptional.get().getUsername(), is("random-name"));
        assertThat(userOptional.get().getLoginCount(), is(0));
        assertThat(userOptional.get().isDisabled(), is(false));

        UserEntity savedUser = argumentCaptor.getValue();
        assertTrue(within(3, SECONDS, savedUser.getCreatedAt()).matches(savedUser.getUpdatedAt()));
    }

    @Test
    public void shouldReturnEmpty_whenResetLoginAttempt_ifUserNotFound() throws Exception {
        when(userDao.findByUsername("random-name")).thenReturn(Optional.empty());
        Optional<User> userOptional = userServices.resetLoginAttempts("random-name");

        assertFalse(userOptional.isPresent());
    }

    @Test
    public void shouldReturnUser_whenIncrementingSessionVersion_ifUserFound() throws Exception {
        User user = aUser();

        JsonNode node = new ObjectMapper().valueToTree(ImmutableMap.of("value", 1));
        Optional<UserEntity> userEntityOptional = Optional.of(UserEntity.from(user));
        when(userDao.findByUsername("random-name")).thenReturn(userEntityOptional);

        Optional<User> userOptional = userServices.incrementSessionVersion("random-name", Integer.valueOf(node.get("value").asText()));
        assertTrue(userOptional.isPresent());

        assertThat(userOptional.get().getUsername(), is("random-name"));
        assertThat(userOptional.get().getSessionVersion(), is(1));
    }

    private User aUser() {
        return User.from("random-name", "random-password", "random@email.com", "1", "784rh", "8948924");
    }

}