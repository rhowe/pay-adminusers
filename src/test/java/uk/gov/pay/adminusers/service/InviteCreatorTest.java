package uk.gov.pay.adminusers.service;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import uk.gov.pay.adminusers.model.Invite;
import uk.gov.pay.adminusers.model.InviteServiceRequest;
import uk.gov.pay.adminusers.model.Role;
import uk.gov.pay.adminusers.persistence.dao.InviteDao;
import uk.gov.pay.adminusers.persistence.dao.RoleDao;
import uk.gov.pay.adminusers.persistence.dao.UserDao;
import uk.gov.pay.adminusers.persistence.entity.InviteEntity;
import uk.gov.pay.adminusers.persistence.entity.RoleEntity;
import uk.gov.pay.adminusers.persistence.entity.UserEntity;

import javax.ws.rs.WebApplicationException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;

public class InviteCreatorTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private NotificationService notificationService = mock(NotificationService.class);
    private InviteDao inviteDao = mock(InviteDao.class);
    private UserDao userDao = mock(UserDao.class);
    private RoleDao roleDao = mock(RoleDao.class);
    private ArgumentCaptor<InviteEntity> persistedInviteEntity = ArgumentCaptor.forClass(InviteEntity.class);
    private InviteCreator inviteCreator;

    @Before
    public void before() throws Exception {
        inviteCreator = new InviteCreator(inviteDao, userDao, roleDao, new LinksBuilder("http://localhost/"), "http://selfservice/", notificationService);
    }

    @Test
    public void shouldSuccess_serviceInvite_IfEmailDoesNotConflict() throws Exception {
        String email = "email@example.gov.uk";
        InviteServiceRequest request = new InviteServiceRequest("password", email, "08976543215");
        when(userDao.findByEmail(email)).thenReturn(Optional.empty());
        when(inviteDao.findByEmail(email)).thenReturn(Optional.empty());
        RoleEntity roleEntity = new RoleEntity(Role.role(2, "admin", "Adminstrator"));
        when(roleDao.findByRoleName("admin")).thenReturn(Optional.of(roleEntity));
        when(notificationService.sendServiceInviteEmail(eq(email), anyString())).thenReturn(CompletableFuture.completedFuture("done"));
        Invite invite = inviteCreator.doCreate(request);

        verify(inviteDao, times(1)).persist(persistedInviteEntity.capture());
        assertThat(invite.getEmail(), is(request.getEmail()));
        assertThat(invite.getTelephoneNumber(), is(request.getTelephoneNumber()));
        assertThat(invite.getType(), is("service"));
        assertThat(invite.getLinks().get(0).getHref(), matchesPattern("^http://selfservice/invites/[0-9a-z]{32}$"));

    }

    @Test
    public void shouldSuccess_serviceInvite_evenIfNotifyThrowsAnError() throws Exception {
        String email = "email@example.gov.uk";
        InviteServiceRequest request = new InviteServiceRequest("password", email, "08976543215");
        when(userDao.findByEmail(email)).thenReturn(Optional.empty());
        when(inviteDao.findByEmail(email)).thenReturn(Optional.empty());
        RoleEntity roleEntity = new RoleEntity(Role.role(2, "admin", "Adminstrator"));
        when(roleDao.findByRoleName("admin")).thenReturn(Optional.of(roleEntity));
        when(notificationService.sendServiceInviteEmail(eq(email), anyString())).thenReturn(CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("done");
        }));
        Invite invite = inviteCreator.doCreate(request);

        verify(inviteDao, times(1)).persist(persistedInviteEntity.capture());
        assertThat(invite.getEmail(), is(request.getEmail()));
        assertThat(invite.getTelephoneNumber(), is(request.getTelephoneNumber()));
        assertThat(invite.getType(), is("service"));
        assertThat(invite.getLinks().get(0).getHref(), matchesPattern("^http://selfservice/invites/[0-9a-z]{32}$"));

    }

    @Test
    public void shouldError_ifUserAlreadyExistsWithGivenEmail() throws Exception {
        String email = "email@example.gov.uk";
        InviteServiceRequest request = new InviteServiceRequest("password", email, "08976543215");
        UserEntity existingUserEntity = new UserEntity();
        when(userDao.findByEmail(email)).thenReturn(Optional.of(existingUserEntity));

        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("HTTP 409 Conflict");

        inviteCreator.doCreate(request);

    }

    @Test
    public void shouldError_ifUserAlreadyHasAValidInvitationWithGivenEmail() throws Exception {
        String email = "email@example.gov.uk";
        InviteServiceRequest request = new InviteServiceRequest("password", email, "08976543215");
        when(userDao.findByEmail(email)).thenReturn(Optional.empty());
        InviteEntity validInvite = mock(InviteEntity.class);
        when(validInvite.isExpired()).thenReturn(false);
        when(validInvite.isDisabled()).thenReturn(false);
        when(inviteDao.findByEmail(email)).thenReturn(Optional.of(validInvite));

        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("HTTP 409 Conflict");

        inviteCreator.doCreate(request);
    }

    @Test
    public void shouldError_ifRoleDoesNotExists() throws Exception {
        String email = "email@example.gov.uk";
        InviteServiceRequest request = new InviteServiceRequest("password", email, "08976543215");
        when(userDao.findByEmail(email)).thenReturn(Optional.empty());
        when(inviteDao.findByEmail(email)).thenReturn(Optional.empty());
        when(roleDao.findByRoleName("admin")).thenReturn(Optional.empty());

        thrown.expect(WebApplicationException.class);
        thrown.expectMessage("HTTP 500 Internal Server Error");

        inviteCreator.doCreate(request);
    }
}
