package com.loan.controller;

import com.loan.dto.AddNomineeRequest;
import com.loan.dto.ApplyLoanRequest;
import com.loan.dto.LoanDetailsView;
import com.loan.dto.LoanTrackerResponse;
import com.loan.entity.Loan;
import com.loan.entity.LoanOffering;
import com.loan.entity.Nominee;
import com.loan.repository.NomineeRepository;
import com.loan.service.LoanOfferingService;
import com.loan.service.LoanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViewControllerTest {

    @Mock
    private LoanService loanService;

    @Mock
    private LoanOfferingService loanOfferingService;

    @Mock
    private NomineeRepository nomineeRepository;

    @InjectMocks
    private ViewController viewController;

    @Test
    void homePopulatesBaseModelWithoutOptionalSections() {
        List<LoanOffering> offerings = List.of(LoanOffering.builder().id(1L).loanType("Home Loan").build());
        Model model = new ExtendedModelMap();

        when(loanOfferingService.getAllLoanOfferings()).thenReturn(offerings);

        String viewName = viewController.home(null, null, null, model);

        assertEquals("index", viewName);
        assertEquals(offerings, model.getAttribute("offerings"));
        assertInstanceOf(ApplyLoanRequest.class, model.getAttribute("applyLoanRequest"));
        assertInstanceOf(AddNomineeRequest.class, model.getAttribute("addNomineeRequest"));
        assertNull(model.getAttribute("selectedUserId"));
        assertNull(model.getAttribute("selectedLoanId"));
        assertNull(model.getAttribute("selectedDetailsLoanId"));
        assertNull(model.getAttribute("userLoans"));
        assertNull(model.getAttribute("tracker"));
        assertNull(model.getAttribute("loanDetails"));
    }

    @Test
    void homePopulatesOptionalSectionsWhenIdsAreProvided() {
        Long userId = 4L;
        Long loanId = 8L;
        LoanOffering offering = LoanOffering.builder().id(3L).loanType("Home Loan").build();
        Loan loan = Loan.builder()
                .id(loanId)
                .loanNumber("LN-8")
                .offering(offering)
                .totalAmount(500000.0)
                .tenure(240)
                .interestRate(8.25)
                .principalOutstanding(450000.0)
                .emiRemaining(200)
                .build();
        Nominee nominee = Nominee.builder()
                .nomineeName("Chris")
                .relationship("Spouse")
                .phone("9999999999")
                .build();
        LoanTrackerResponse tracker = LoanTrackerResponse.builder()
                .loanNumber("LN-8")
                .loanAmount(500000.0)
                .principalOutstanding(450000.0)
                .interestRate(8.25)
                .emiRemaining(200)
                .build();
        List<Loan> loans = List.of(loan);
        Model model = new ExtendedModelMap();

        when(loanOfferingService.getAllLoanOfferings()).thenReturn(List.of(offering));
        when(loanService.getLoansByUser(userId)).thenReturn(loans);
        when(loanService.getLoanTracker(loanId)).thenReturn(tracker);
        when(loanService.getLoanDetails(loanId)).thenReturn(loan);
        when(nomineeRepository.findByLoanId(loanId)).thenReturn(Optional.of(nominee));

        String viewName = viewController.home(userId, loanId, loanId, model);

        assertEquals("index", viewName);
        assertEquals(loans, model.getAttribute("userLoans"));
        assertEquals(tracker, model.getAttribute("tracker"));
        LoanDetailsView details = (LoanDetailsView) model.getAttribute("loanDetails");
        assertEquals(loanId, details.getLoanId());
        assertEquals("LN-8", details.getLoanNumber());
        assertEquals("Home Loan", details.getLoanType());
        assertEquals("Chris", details.getNomineeName());
        assertEquals("Spouse", details.getNomineeRelationship());
        assertEquals("9999999999", details.getNomineePhone());
        assertEquals(500000.0, details.getTotalLoanAmount());
        assertEquals(240, details.getLoanTenure());
        assertEquals(8.25, details.getCurrentInterestRate());
        assertEquals(450000.0, details.getPrincipalOutstandingAmount());
        assertEquals(200, details.getOutstandingEmiCount());
    }

    @Test
    void homeUsesFallbackValuesWhenLoanOfferingOrNomineeAreMissing() {
        Long loanId = 12L;
        Loan loan = Loan.builder()
                .id(loanId)
                .loanNumber("LN-12")
                .totalAmount(250000.0)
                .tenure(120)
                .interestRate(7.5)
                .principalOutstanding(200000.0)
                .emiRemaining(95)
                .build();
        Model model = new ExtendedModelMap();

        when(loanOfferingService.getAllLoanOfferings()).thenReturn(List.of());
        when(loanService.getLoanDetails(loanId)).thenReturn(loan);
        when(nomineeRepository.findByLoanId(loanId)).thenReturn(Optional.empty());

        String viewName = viewController.home(null, null, loanId, model);

        assertEquals("index", viewName);
        LoanDetailsView details = (LoanDetailsView) model.getAttribute("loanDetails");
        assertEquals("Not available", details.getLoanType());
        assertEquals("Not added", details.getNomineeName());
        assertEquals("Not added", details.getNomineeRelationship());
        assertEquals("Not added", details.getNomineePhone());
    }

    @Test
    void applyLoanAddsSuccessMessageAndLoadsLoanData() {
        ApplyLoanRequest request = new ApplyLoanRequest();
        request.setUserId(6L);
        request.setOfferingId(2L);
        request.setLoanAmount(300000.0);
        request.setTenure(180);

        LoanOffering offering = LoanOffering.builder().id(2L).loanType("Home Loan").build();
        Loan loan = Loan.builder()
                .id(15L)
                .loanNumber("LN-15")
                .offering(offering)
                .totalAmount(300000.0)
                .tenure(180)
                .interestRate(8.0)
                .principalOutstanding(300000.0)
                .emiRemaining(180)
                .build();
        LoanTrackerResponse tracker = LoanTrackerResponse.builder()
                .loanNumber("LN-15")
                .loanAmount(300000.0)
                .principalOutstanding(300000.0)
                .interestRate(8.0)
                .emiRemaining(180)
                .build();
        Model model = new ExtendedModelMap();

        when(loanService.applyLoan(request)).thenReturn(loan);
        when(loanOfferingService.getAllLoanOfferings()).thenReturn(List.of(offering));
        when(loanService.getLoansByUser(6L)).thenReturn(List.of(loan));
        when(loanService.getLoanTracker(15L)).thenReturn(tracker);
        when(loanService.getLoanDetails(15L)).thenReturn(loan);
        when(nomineeRepository.findByLoanId(15L)).thenReturn(Optional.empty());

        String viewName = viewController.applyLoan(request, model);

        assertEquals("index", viewName);
        assertEquals("Loan application submitted. Loan ID: 15", model.getAttribute("successMessage"));
        assertEquals(List.of(loan), model.getAttribute("userLoans"));
        verify(loanService).applyLoan(request);
    }

    @Test
    void applyLoanAddsErrorMessageWhenServiceThrows() {
        ApplyLoanRequest request = new ApplyLoanRequest();
        request.setUserId(7L);
        Model model = new ExtendedModelMap();

        when(loanService.applyLoan(request)).thenThrow(new RuntimeException("User not found"));
        when(loanOfferingService.getAllLoanOfferings()).thenReturn(List.of());
        when(loanService.getLoansByUser(7L)).thenReturn(List.of());

        String viewName = viewController.applyLoan(request, model);

        assertEquals("index", viewName);
        assertEquals("User not found", model.getAttribute("errorMessage"));
        assertEquals(List.of(), model.getAttribute("userLoans"));
    }

    @Test
    void addNomineeAddsSuccessMessageAndLoadsLoanSections() {
        AddNomineeRequest request = new AddNomineeRequest();
        request.setLoanId(20L);
        request.setNomineeName("Jamie");
        request.setRelationship("Parent");
        request.setPhone("8888888888");

        Loan loan = Loan.builder()
                .id(20L)
                .loanNumber("LN-20")
                .totalAmount(400000.0)
                .tenure(240)
                .interestRate(7.9)
                .principalOutstanding(350000.0)
                .emiRemaining(170)
                .build();
        LoanTrackerResponse tracker = LoanTrackerResponse.builder()
                .loanNumber("LN-20")
                .loanAmount(400000.0)
                .principalOutstanding(350000.0)
                .interestRate(7.9)
                .emiRemaining(170)
                .build();
        Nominee nominee = Nominee.builder()
                .nomineeName("Jamie")
                .relationship("Parent")
                .phone("8888888888")
                .build();
        Model model = new ExtendedModelMap();

        doNothing().when(loanService).addNominee(request);
        when(loanOfferingService.getAllLoanOfferings()).thenReturn(List.of());
        when(loanService.getLoanTracker(20L)).thenReturn(tracker);
        when(loanService.getLoanDetails(20L)).thenReturn(loan);
        when(nomineeRepository.findByLoanId(20L)).thenReturn(Optional.of(nominee));

        String viewName = viewController.addNominee(request, model);

        assertEquals("index", viewName);
        assertEquals("Nominee added successfully.", model.getAttribute("successMessage"));
        assertEquals(tracker, model.getAttribute("tracker"));
        verify(loanService).addNominee(request);
    }

    @Test
    void addNomineeAddsErrorMessageWhenServiceThrows() {
        AddNomineeRequest request = new AddNomineeRequest();
        request.setLoanId(21L);
        Model model = new ExtendedModelMap();

        doThrow(new RuntimeException("Loan not found")).when(loanService).addNominee(request);
        when(loanOfferingService.getAllLoanOfferings()).thenReturn(List.of());
        when(loanService.getLoanTracker(21L)).thenReturn(LoanTrackerResponse.builder().build());
        when(loanService.getLoanDetails(21L)).thenReturn(Loan.builder().id(21L).loanNumber("LN-21").build());
        when(nomineeRepository.findByLoanId(21L)).thenReturn(Optional.empty());

        String viewName = viewController.addNominee(request, model);

        assertEquals("index", viewName);
        assertEquals("Loan not found", model.getAttribute("errorMessage"));
    }

    @Test
    void viewHelperEndpointsDelegateToHome() {
        Model loansModel = new ExtendedModelMap();
        when(loanOfferingService.getAllLoanOfferings()).thenReturn(List.of());
        when(loanService.getLoansByUser(3L)).thenReturn(List.of());

        assertEquals("index", viewController.viewLoansByUser(3L, loansModel));
        assertEquals(List.of(), loansModel.getAttribute("userLoans"));

        Model trackerModel = new ExtendedModelMap();
        when(loanOfferingService.getAllLoanOfferings()).thenReturn(List.of());
        LoanTrackerResponse tracker = LoanTrackerResponse.builder().loanNumber("LN-9").build();
        when(loanService.getLoanTracker(9L)).thenReturn(tracker);

        assertEquals("index", viewController.viewTracker(9L, trackerModel));
        assertEquals(tracker, trackerModel.getAttribute("tracker"));

        Model detailsModel = new ExtendedModelMap();
        when(loanOfferingService.getAllLoanOfferings()).thenReturn(List.of());
        when(loanService.getLoanDetails(10L)).thenReturn(Loan.builder().id(10L).loanNumber("LN-10").build());
        when(nomineeRepository.findByLoanId(10L)).thenReturn(Optional.empty());

        assertEquals("index", viewController.viewLoanDetails(10L, detailsModel));
        assertInstanceOf(LoanDetailsView.class, detailsModel.getAttribute("loanDetails"));
    }
}
