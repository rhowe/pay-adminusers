package uk.gov.pay.adminusers.service;

import com.google.inject.Inject;
import org.slf4j.Logger;
import uk.gov.pay.adminusers.logger.PayLoggerFactory;
import uk.gov.pay.adminusers.model.StripeAgreement;
import uk.gov.pay.adminusers.persistence.dao.StripeAgreementDao;
import uk.gov.pay.adminusers.persistence.entity.StripeAgreementEntity;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.Optional;

import static java.lang.String.format;

public class StripeAgreementService {

    private static Logger logger = PayLoggerFactory.getLogger(StripeAgreementService.class);

    private final StripeAgreementDao stripeAgreementDao;

    @Inject
    public StripeAgreementService(StripeAgreementDao stripeAgreementDao) {
        this.stripeAgreementDao = stripeAgreementDao;
    }

    public Optional<StripeAgreement> findStripeAgreementByServiceId(int serviceId) {
        return stripeAgreementDao.findByServiceId(serviceId)
                .map((StripeAgreementEntity::toStripeAgreement));
    }
    
    public void doCreate(int serviceId, InetAddress ipAddress, LocalDateTime agreementTime) {
        logger.info(format("Creating stripe agreement for service %s", serviceId));
        stripeAgreementDao.persist(new StripeAgreementEntity(serviceId, ipAddress.getHostAddress(), agreementTime));
    }
}
