import React, { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import {
   X, Shield, ChevronDown, ChevronUp, Info, Check, CheckSquare, Square,
   Lock, Star, Clock, AlertTriangle, HelpCircle, FileText,
   Download, Edit3, Wifi, Monitor, Trash2, Ban, MessageSquare,
   CheckCircle, XCircle, Loader2
} from 'lucide-react';
import Button from '../../components/common/Button';
import IconButton from '../../components/common/IconButton';
import Badge from '../../components/common/Badge';
import {
   getConsentFormByCategory,
   privacyNotices,
   requiredAcknowledgments,
   expirationOptions,
} from '../../mocks/consentForm';
import { useAuth } from '../../contexts/AuthContext';
import { consentAPI } from '../../services/api';

// Step indicator component
const StepIndicator = ({ currentStep, totalSteps }) => (
   <div className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-slate-400">
      <span className="font-medium">Step {currentStep}/{totalSteps}</span>
      <div className="flex gap-0.5">
         {Array.from({ length: totalSteps }, (_, i) => (
            <div
               key={i}
               className={`h-1.5 w-5 rounded-full transition-colors ${
                  i < currentStep ? 'bg-blue-500' : 'bg-gray-200 dark:bg-slate-700'
               }`}
            />
         ))}
      </div>
   </div>
);

// Consent option card component
const ConsentOptionCard = ({
   option,
   isSelected,
   onToggle,
   isExpanded,
   onToggleExpand,
   disabled
}) => {
   return (
      <div
         className={`border rounded p-3 transition-all duration-200 ${
            isSelected
               ? 'border-blue-500 border-2 bg-blue-50/30 dark:bg-blue-900/20'
               : 'border-gray-200 dark:border-slate-700 hover:border-gray-300 dark:hover:border-slate-600 hover:shadow-sm'
         } ${disabled ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'}`}
         onClick={() => !disabled && !option.required && onToggle(option.id)}
      >
         <div className="flex items-start gap-2">
            {/* Checkbox */}
            <button
               onClick={(e) => {
                  e.stopPropagation();
                  if (!disabled && !option.required) onToggle(option.id);
               }}
               disabled={disabled || option.required}
               className={`flex-shrink-0 w-5 h-5 rounded flex items-center justify-center transition-all ${
                  isSelected
                     ? 'bg-blue-500 text-white'
                     : 'bg-white dark:bg-slate-800 border-2 border-gray-300 dark:border-slate-600'
               } ${option.required ? 'cursor-not-allowed' : 'hover:border-blue-400'}`}
               aria-label={isSelected ? 'Deselect option' : 'Select option'}
            >
               {isSelected && <Check className="w-3 h-3" />}
               {option.required && !isSelected && <Lock className="w-2.5 h-2.5 text-gray-400 dark:text-slate-500" />}
            </button>

            {/* Content */}
            <div className="flex-1 min-w-0">
               <div className="flex items-start justify-between gap-1.5">
                  <div>
                     <div className="flex items-center gap-1.5 flex-wrap">
                        <h4 className="font-semibold text-gray-900 dark:text-slate-100 text-sm">{option.title}</h4>
                        {option.required && (
                           <span className="px-1.5 py-0.5 rounded-full text-xs font-semibold bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-300 flex items-center gap-0.5">
                              <Lock className="w-2.5 h-2.5" /> REQ
                           </span>
                        )}
                        {option.recommended && !option.required && (
                           <span className="px-1.5 py-0.5 rounded-full text-xs font-semibold bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300 flex items-center gap-0.5">
                              <Star className="w-2.5 h-2.5" /> REC
                           </span>
                        )}
                     </div>
                     <p className="text-xs text-gray-600 dark:text-slate-300 mt-0.5">{option.description}</p>
                  </div>
               </div>

               {/* Conditional Warning */}
               {option.conditionalWarning && (
                  <div className="mt-2 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded p-2">
                     <div className="flex items-start gap-1.5">
                        <AlertTriangle className="w-3.5 h-3.5 text-yellow-600 dark:text-yellow-400 flex-shrink-0 mt-0.5" />
                        <p className="text-xs text-yellow-800 dark:text-yellow-300">{option.conditionalWarning}</p>
                     </div>
                  </div>
               )}

               {/* Contact Field (for communications) */}
               {option.contactField && isSelected && (
                  <div className="mt-2 bg-gray-50 dark:bg-slate-800/50 rounded p-2">
                     <label className="block text-xs font-medium text-gray-700 dark:text-slate-200 mb-0.5">
                        {option.contactField.label}
                     </label>
                     <input
                        type={option.contactField.type === 'email' ? 'email' : 'tel'}
                        defaultValue={option.contactField.value}
                        className="w-full px-2 py-1 border border-gray-300 dark:border-slate-600 rounded text-xs focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 focus:border-blue-500 dark:focus:border-blue-400 bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                        onClick={(e) => e.stopPropagation()}
                        readOnly={!option.contactField.editable}
                     />
                  </div>
               )}

               {/* Expand/Collapse for more info */}
               {option.expandedInfo && (
                  <button
                     onClick={(e) => {
                        e.stopPropagation();
                        onToggleExpand(option.id);
                     }}
                     className="mt-2 text-xs text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 flex items-center gap-0.5"
                  >
                     {isExpanded ? (
                        <>
                           <ChevronUp className="w-4 h-4" /> Less info
                        </>
                     ) : (
                        <>
                           <ChevronDown className="w-4 h-4" /> More info
                        </>
                     )}
                  </button>
               )}

               {/* Expanded Info */}
               {isExpanded && option.expandedInfo && (
                  <div className="mt-3 bg-gray-50 dark:bg-slate-800/50 rounded-lg p-4 space-y-3 animate-fade-in">
                     <div>
                        <p className="text-xs font-semibold text-gray-500 dark:text-slate-400 uppercase">Who receives this</p>
                        <p className="text-sm text-gray-700 dark:text-slate-200">{option.expandedInfo.whoReceives}</p>
                     </div>
                     <div>
                        <p className="text-xs font-semibold text-gray-500 dark:text-slate-400 uppercase">How it's used</p>
                        <p className="text-sm text-gray-700 dark:text-slate-200">{option.expandedInfo.howUsed}</p>
                     </div>
                     <div>
                        <p className="text-xs font-semibold text-gray-500 dark:text-slate-400 uppercase">How to revoke</p>
                        <p className="text-sm text-gray-700 dark:text-slate-200">{option.expandedInfo.howToRevoke}</p>
                     </div>
                  </div>
               )}
            </div>
         </div>
      </div>
   );
};

// Signature pad component (simplified - in production use a real signature library)
const SignaturePad = ({ value, onChange, method, onMethodChange }) => {
   const canvasRef = useRef(null);
   const [isDrawing, setIsDrawing] = useState(false);

   const clearCanvas = () => {
      const canvas = canvasRef.current;
      if (canvas) {
         const ctx = canvas.getContext('2d');
         ctx.clearRect(0, 0, canvas.width, canvas.height);
         onChange('');
      }
   };

   const startDrawing = (e) => {
      const canvas = canvasRef.current;
      const ctx = canvas.getContext('2d');
      const rect = canvas.getBoundingClientRect();
      const x = (e.clientX || e.touches?.[0]?.clientX) - rect.left;
      const y = (e.clientY || e.touches?.[0]?.clientY) - rect.top;
      
      ctx.beginPath();
      ctx.moveTo(x, y);
      setIsDrawing(true);
   };

   const draw = (e) => {
      if (!isDrawing) return;
      const canvas = canvasRef.current;
      const ctx = canvas.getContext('2d');
      const rect = canvas.getBoundingClientRect();
      const x = (e.clientX || e.touches?.[0]?.clientX) - rect.left;
      const y = (e.clientY || e.touches?.[0]?.clientY) - rect.top;
      
      ctx.lineTo(x, y);
      ctx.strokeStyle = '#1f2937';
      ctx.lineWidth = 2;
      ctx.lineCap = 'round';
      ctx.stroke();
   };

   const stopDrawing = () => {
      setIsDrawing(false);
      if (canvasRef.current) {
         onChange(canvasRef.current.toDataURL());
      }
   };

   return (
      <div className="space-y-4">
         <div className="flex gap-4">
            <button
               onClick={() => onMethodChange('typed')}
               className={`flex-1 py-3 px-4 rounded-lg border-2 transition-all ${
                  method === 'typed'
                     ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300'
                     : 'border-gray-200 dark:border-slate-700 text-gray-600 dark:text-slate-300 hover:border-gray-300 dark:hover:border-slate-600'
               }`}
            >
               Type Name
            </button>
            <button
               onClick={() => onMethodChange('drawn')}
               className={`flex-1 py-3 px-4 rounded-lg border-2 transition-all ${
                  method === 'drawn'
                     ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300'
                     : 'border-gray-200 dark:border-slate-700 text-gray-600 dark:text-slate-300 hover:border-gray-300 dark:hover:border-slate-600'
               }`}
            >
               Draw Signature
            </button>
         </div>

         {method === 'typed' && (
            <div className="space-y-2">
               <input
                  type="text"
                  value={value}
                  onChange={(e) => onChange(e.target.value)}
                  placeholder="Type your full legal name"
                  className="w-full px-4 py-3 border-2 border-gray-200 dark:border-slate-700 rounded-lg text-lg focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 focus:border-blue-500 dark:focus:border-blue-400 bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
               />
               <label className="flex items-start gap-2 text-sm text-gray-600 dark:text-slate-300">
                  <input type="checkbox" className="mt-1" required />
                  <span>By typing my name, I agree this serves as my legal signature</span>
               </label>
            </div>
         )}

         {method === 'drawn' && (
            <div className="space-y-2">
               <div className="relative">
                  <canvas
                     ref={canvasRef}
                     width={500}
                     height={150}
                     className="w-full border-2 border-dashed border-gray-300 dark:border-slate-600 rounded-lg bg-gray-50 dark:bg-slate-800/50 cursor-crosshair touch-none"
                     onMouseDown={startDrawing}
                     onMouseMove={draw}
                     onMouseUp={stopDrawing}
                     onMouseLeave={stopDrawing}
                     onTouchStart={startDrawing}
                     onTouchMove={draw}
                     onTouchEnd={stopDrawing}
                  />
                  {!value && (
                     <div className="absolute inset-0 flex items-center justify-center pointer-events-none text-gray-400 dark:text-slate-500">
                        Sign here
                     </div>
                  )}
               </div>
               <button
                  onClick={clearCanvas}
                  className="text-sm text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300"
               >
                  Clear signature
               </button>
            </div>
         )}

         <div className="text-sm text-gray-500 dark:text-slate-400">
            <p>Date: {new Date().toLocaleDateString('en-US', { 
               year: 'numeric', 
               month: 'long', 
               day: 'numeric',
               hour: 'numeric',
               minute: '2-digit'
            })}</p>
         </div>
      </div>
   );
};

// History timeline item component
const HistoryTimelineItem = ({ item, isLast }) => {
   const [isExpanded, setIsExpanded] = useState(false);

   const formatDateTime = (timestamp) => {
      const date = new Date(timestamp);
      return {
         date: date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }),
         time: date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
      };
   };

   const { date, time } = formatDateTime(item.timestamp);

   const getActionBadge = (action) => {
      switch (action) {
         case 'granted':
            return <Badge type="green">Granted</Badge>;
         case 'withdrawn':
            return <Badge type="red">Withdrawn</Badge>;
         case 'modified':
            return <Badge type="yellow">Modified</Badge>;
         default:
            return <Badge>{action}</Badge>;
      }
   };

   return (
      <div className="relative pl-10">
         {/* Timeline line */}
         {!isLast && (
            <div className="absolute left-4 top-8 bottom-0 w-0.5 bg-gray-200 dark:bg-slate-700" />
         )}
         
         {/* Timeline dot */}
         <div className={`absolute left-2 w-5 h-5 rounded-full flex items-center justify-center ${
            item.action === 'granted' ? 'bg-green-100 dark:bg-green-900/30' :
            item.action === 'withdrawn' ? 'bg-red-100 dark:bg-red-900/30' :
            'bg-yellow-100 dark:bg-yellow-900/30'
         }`}>
            {item.action === 'granted' && <Check className="w-3 h-3 text-green-600 dark:text-green-400" />}
            {item.action === 'withdrawn' && <X className="w-3 h-3 text-red-600 dark:text-red-400" />}
            {item.action === 'modified' && <Edit3 className="w-3 h-3 text-yellow-600 dark:text-yellow-400" />}
         </div>
         
         {/* Content */}
         <div className="bg-gray-50 dark:bg-slate-800/50 rounded-lg p-4 mb-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-2">
               <div className="flex items-center gap-2">
                  {getActionBadge(item.action)}
                  <span className="text-sm text-gray-500 dark:text-slate-400">by {item.changedBy.name}</span>
               </div>
               <div className="text-sm text-gray-500 dark:text-slate-400">
                  {date} at {time}
               </div>
            </div>

            {/* Changes summary */}
            {item.changes && (
               <div className="space-y-1 text-sm">
                  {item.changes.added.length > 0 && (
                     <p className="text-green-700 dark:text-green-400">
                        <span className="font-medium">Added:</span> {item.changes.added.join(', ')}
                     </p>
                  )}
                  {item.changes.removed.length > 0 && (
                     <p className="text-red-700 dark:text-red-400">
                        <span className="font-medium">Removed:</span> {item.changes.removed.join(', ')}
                     </p>
                  )}
               </div>
            )}

            {item.reason && (
               <p className="text-sm text-gray-600 dark:text-slate-300 mt-2">
                  <span className="font-medium">Reason:</span> {item.reason}
               </p>
            )}

            {/* Expand for metadata */}
            <button
               onClick={() => setIsExpanded(!isExpanded)}
               className="text-sm text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 mt-2 flex items-center gap-1"
            >
               {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
               View details
            </button>

            {isExpanded && (
               <div className="mt-3 pt-3 border-t border-gray-200 dark:border-slate-700 text-sm text-gray-500 dark:text-slate-400 space-y-1 animate-fade-in">
                  <div className="flex items-center gap-2">
                     <Wifi className="w-4 h-4" />
                     <span>IP Address: {item.metadata.ipAddress}</span>
                  </div>
                  <div className="flex items-center gap-2">
                     <Monitor className="w-4 h-4" />
                     <span>Device: {item.metadata.device}</span>
                  </div>
                  <div className="flex items-center gap-2">
                     <FileText className="w-4 h-4" />
                     <span>Version: {item.metadata.consentVersion}</span>
                  </div>
                  <IconButton 
                     icon={Download} 
                     label="Download record (PDF)" 
                     variant="ghost"
                     size="sm" 
                     className="mt-2 text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300"
                  />
               </div>
            )}
         </div>
      </div>
   );
};

// Main Grant/Modify Consent Component
const GrantModifyConsent = ({
   isOpen,
   onClose,
   category,
   mode = 'grant', // 'grant' or 'modify'
   existingSelections = [],
   onSubmit,
   consentId,        // real consent DB id for revoke (required when mode='modify')
   grantedToId,      // provider userId to grant consent to
   backendConsentType, // backend consentType (ALL, MEDICAL_RECORDS, LAB_RESULTS, etc.)
   patientHistory = [], // real consent history from parent
}) => {
   const { user } = useAuth();
   // Get form data based on category
   const formData = useMemo(() => getConsentFormByCategory(category), [category]);

   // Form state
   const [step, setStep] = useState(1); // eslint-disable-line no-unused-vars
   const [selectedOptions, setSelectedOptions] = useState([]);;
   const [expandedOptions, setExpandedOptions] = useState({});
   const [effectiveDate, setEffectiveDate] = useState('immediate');
   const [customEffectiveDate, setCustomEffectiveDate] = useState('');
   const [expiration, setExpiration] = useState('none');
   const [customExpirationDate, setCustomExpirationDate] = useState('');
   const [acknowledgments, setAcknowledgments] = useState({});
   const [signatureMethod, setSignatureMethod] = useState('typed');
   const [signatureValue, setSignatureValue] = useState('');
   const [expandedSections, setExpandedSections] = useState({});
   
   // UI state
   const [showConfirmModal, setShowConfirmModal] = useState(false);
   const [showSuccessScreen, setShowSuccessScreen] = useState(false);
   const [isSubmitting, setIsSubmitting] = useState(false);
   const [toast, setToast] = useState(null);
   const [countdown, setCountdown] = useState(5);
   const [generatedConsentId, setGeneratedConsentId] = useState('');
   const [showHistory, setShowHistory] = useState(false);
   
   // Withdrawal flow state
   const [showWithdrawModal, setShowWithdrawModal] = useState(false);
   const [withdrawStep, setWithdrawStep] = useState(1); // 1: warning, 2: confirm, 3: success
   const [withdrawReason, setWithdrawReason] = useState('');
   const [withdrawReasonOther, setWithdrawReasonOther] = useState('');
   const [withdrawConfirmText, setWithdrawConfirmText] = useState('');
   const [withdrawEffective, setWithdrawEffective] = useState('immediate');
   const [isWithdrawing, setIsWithdrawing] = useState(false);
   const [withdrawConsentId, setWithdrawConsentId] = useState('');

   // Auto-save draft timer

   // Initialize with existing selections if modifying
   useEffect(() => {
      if (mode === 'modify' && existingSelections.length > 0) {
         setSelectedOptions(existingSelections);
      }
      // Pre-select required options
      if (formData?.availableOptions) {
         const requiredOptions = formData.availableOptions
            .filter(opt => opt.required)
            .map(opt => opt.id);
         setSelectedOptions(prev => [...new Set([...prev, ...requiredOptions])]);
      }
   }, [mode, existingSelections, formData]);

   // Show toast notification
   const showToast = useCallback((type, message) => {
      setToast({ type, message });
      setTimeout(() => setToast(null), 4000);
   }, []);

   // Countdown timer for success screen
   useEffect(() => {
      if (showSuccessScreen && countdown > 0) {
         const timer = setTimeout(() => setCountdown(prev => prev - 1), 1000);
         return () => clearTimeout(timer);
      } else if (showSuccessScreen && countdown === 0) {
         onClose();
      }
   }, [showSuccessScreen, countdown, onClose]);

   // Toggle option selection
   const toggleOption = useCallback((optionId) => {
      setSelectedOptions(prev => 
         prev.includes(optionId)
            ? prev.filter(id => id !== optionId)
            : [...prev, optionId]
      );
   }, []);

   // Toggle expand option info
   const toggleExpandOption = useCallback((optionId) => {
      setExpandedOptions(prev => ({
         ...prev,
         [optionId]: !prev[optionId]
      }));
   }, []);

   // Toggle expand section
   const toggleSection = useCallback((sectionId) => {
      setExpandedSections(prev => ({
         ...prev,
         [sectionId]: !prev[sectionId]
      }));
   }, []);

   // Select all options
   const selectAll = useCallback(() => {
      if (formData?.availableOptions) {
         setSelectedOptions(formData.availableOptions.map(opt => opt.id));
      }
   }, [formData]);

   // Deselect all (except required)
   const deselectAll = useCallback(() => {
      if (formData?.availableOptions) {
         const requiredOptions = formData.availableOptions
            .filter(opt => opt.required)
            .map(opt => opt.id);
         setSelectedOptions(requiredOptions);
      }
   }, [formData]);

   // Toggle acknowledgment
   const toggleAcknowledgment = useCallback((id) => {
      setAcknowledgments(prev => ({
         ...prev,
         [id]: !prev[id]
      }));
   }, []);

   // Check if form is valid for submission
   const isFormValid = useMemo(() => {
      // At least one option selected
      if (selectedOptions.length === 0) return false;
      
      // All required acknowledgments checked
      const requiredAcks = requiredAcknowledgments.filter(a => a.required);
      const allRequiredChecked = requiredAcks.every(a => acknowledgments[a.id]);
      if (!allRequiredChecked) return false;
      
      // Signature provided
      if (!signatureValue.trim()) return false;
      
      // Custom dates valid if selected
      if (effectiveDate === 'custom' && !customEffectiveDate) return false;
      if (expiration === 'custom' && !customExpirationDate) return false;
      
      return true;
   }, [selectedOptions, acknowledgments, signatureValue, effectiveDate, customEffectiveDate, expiration, customExpirationDate]);

   // Get validation message
   const getValidationMessage = () => {
      if (selectedOptions.length === 0) return 'Please select at least one consent option';
      const requiredAcks = requiredAcknowledgments.filter(a => a.required);
      const missingAck = requiredAcks.find(a => !acknowledgments[a.id]);
      if (missingAck) return `Please acknowledge: "${missingAck.text}"`;
      if (!signatureValue.trim()) return 'Please provide your signature';
      if (effectiveDate === 'custom' && !customEffectiveDate) return 'Please select an effective date';
      if (expiration === 'custom' && !customExpirationDate) return 'Please select an expiration date';
      return '';
   };

   // Handle review (go to confirmation)
   const handleReview = () => {
      setShowConfirmModal(true);
   };

   // Handle final submit
   const handleSubmit = async () => {
      setIsSubmitting(true);
      try {
         let expiresAt = null;
         if (expiration === '6-months') {
            expiresAt = new Date(Date.now() + 6 * 30 * 24 * 60 * 60 * 1000).toISOString();
         } else if (expiration === '1-year') {
            expiresAt = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString();
         } else if (expiration === '2-years') {
            expiresAt = new Date(Date.now() + 2 * 365 * 24 * 60 * 60 * 1000).toISOString();
         } else if (expiration === '5-years') {
            expiresAt = new Date(Date.now() + 5 * 365 * 24 * 60 * 60 * 1000).toISOString();
         } else if (expiration === 'custom') {
            expiresAt = customExpirationDate ? new Date(customExpirationDate).toISOString() : null;
         }

         const result = (mode === 'modify' && consentId)
            ? await consentAPI.updateConsent(consentId, { expiresAt, reason: signatureValue || '' })
            : await consentAPI.grantConsent({
               grantedToId,
               consentType: backendConsentType,
               reason: signatureValue || '',
               expiresAt,
            });

         setGeneratedConsentId(String(result.id));
         setIsSubmitting(false);
         setShowConfirmModal(false);
         setShowSuccessScreen(true);

         if (onSubmit) {
            onSubmit({
               consentId: result.id,
               category,
               selectedOptions,
               effectiveDate: effectiveDate === 'immediate' ? new Date().toISOString() : customEffectiveDate,
               expiration: expiration === 'none' ? null : expiration === 'custom' ? customExpirationDate : expiration,
               signature: {
                  method: signatureMethod,
                  value: signatureValue,
                  timestamp: new Date().toISOString(),
               }
            });
         }
      } catch (err) {
         console.error('Failed to submit consent:', err);
         setIsSubmitting(false);
         showToast('error', 'Failed to submit consent. Please try again.');
      }
   };

   // Handle cancel with confirmation
   const handleCancel = () => {
      if (selectedOptions.length > 0) {
         if (window.confirm('Are you sure? Your changes will be lost.')) {
            onClose();
         }
      } else {
         onClose();
      }
   };

   // Start withdrawal flow
   const startWithdrawal = () => {
      setWithdrawStep(1);
      setWithdrawReason('');
      setWithdrawReasonOther('');
      setWithdrawConfirmText('');
      setWithdrawEffective('immediate');
      setShowWithdrawModal(true);
   };

   // Handle withdrawal submission
   const handleWithdraw = async () => {
      setIsWithdrawing(true);
      try {
         const result = await consentAPI.revokeConsent(consentId, withdrawReason === 'other' ? withdrawReasonOther : withdrawReason);
         setWithdrawConsentId(String(result?.id || consentId));
         setIsWithdrawing(false);
         setWithdrawStep(3); // Success

         if (onSubmit) {
            onSubmit({
               action: 'withdraw',
               consentId: result?.id || consentId,
               category,
               reason: withdrawReason === 'other' ? withdrawReasonOther : withdrawReason,
               effectiveDate: withdrawEffective,
               timestamp: new Date().toISOString()
            });
         }
      } catch (err) {
         console.error('Failed to withdraw consent:', err);
         setIsWithdrawing(false);
         showToast('error', 'Failed to withdraw consent. Please try again.');
      }
   };

   // Withdrawal reason options
   const withdrawalReasons = [
      { id: 'no-longer-needed', label: 'Services no longer needed' },
      { id: 'privacy-concerns', label: 'Privacy concerns' },
      { id: 'switching-providers', label: 'Switching to another provider' },
      { id: 'prefer-not-share', label: 'Prefer not to share my information' },
      { id: 'dissatisfied', label: 'Dissatisfied with how data was used' },
      { id: 'other', label: 'Other reason' }
   ];

   // Withdrawal implications
   const withdrawalImplications = [
      {
         icon: 'warning',
         title: 'Care Coordination Impact',
         description: 'Your care providers may not be able to share important health information, which could affect the quality of care you receive.'
      },
      {
         icon: 'info',
         title: 'Existing Records',
         description: 'Records already shared under this consent will remain with those providers. This withdrawal only applies to future sharing.'
      },
      {
         icon: 'warning',
         title: 'Emergency Situations',
         description: 'In emergencies, providers may still access your records as permitted by law, regardless of this withdrawal.'
      },
      {
         icon: 'info',
         title: 'Re-Authorization',
         description: 'You can grant consent again at any time if you change your mind.'
      }
   ];

   if (!isOpen || !formData) return null;

   // Withdrawal Modal
   if (showWithdrawModal) {
      return (
         <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 dark:bg-black/70 backdrop-blur-sm">
            <div className="bg-white dark:bg-slate-800 rounded-2xl shadow-2xl max-w-xl w-full mx-4 max-h-[90vh] overflow-hidden flex flex-col animate-fade-in">
               {/* Modal Header */}
               <div className="px-6 py-4 border-b border-gray-100 dark:border-slate-700 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                     <div className={`w-10 h-10 rounded-full flex items-center justify-center ${
                        withdrawStep === 3 ? 'bg-green-100 dark:bg-green-900/30' : 'bg-red-100 dark:bg-red-900/30'
                     }`}>
                        {withdrawStep === 3 ? (
                           <CheckCircle className="w-5 h-5 text-green-600 dark:text-green-400" />
                        ) : (
                           <Ban className="w-5 h-5 text-red-600 dark:text-red-400" />
                        )}
                     </div>
                     <div>
                        <h3 className="font-bold text-gray-900 dark:text-slate-100">
                           {withdrawStep === 1 && 'Withdraw Consent - Important Information'}
                           {withdrawStep === 2 && 'Confirm Withdrawal'}
                           {withdrawStep === 3 && 'Withdrawal Confirmed'}
                        </h3>
                        <p className="text-sm text-gray-500 dark:text-slate-400">
                           {formData.title}
                        </p>
                     </div>
                  </div>
                  {withdrawStep !== 3 && (
                     <button
                        onClick={() => setShowWithdrawModal(false)}
                        className="p-2 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
                     >
                        <X className="w-5 h-5 text-gray-500 dark:text-slate-400" />
                     </button>
                  )}
               </div>

               {/* Modal Content */}
               <div className="flex-1 overflow-y-auto p-6">
                  {/* Step 1: Warning About Implications */}
                  {withdrawStep === 1 && (
                     <div className="space-y-6 animate-fade-in">
                        {/* Strong Warning Banner */}
                        <div className="bg-red-50 dark:bg-red-900/20 border-2 border-red-200 dark:border-red-800 rounded-xl p-5">
                           <div className="flex items-start gap-4">
                              <AlertTriangle className="w-8 h-8 text-red-600 dark:text-red-400 flex-shrink-0" />
                              <div>
                                 <h4 className="text-lg font-bold text-red-900 dark:text-red-300">Please Read Carefully</h4>
                                 <p className="text-red-800 dark:text-red-300 mt-1">
                                    Withdrawing your consent may significantly impact your healthcare coordination.
                                    Please review the implications below before proceeding.
                                 </p>
                              </div>
                           </div>
                        </div>

                        {/* Implications List */}
                        <div className="space-y-4">
                           <h4 className="font-semibold text-gray-800 dark:text-slate-100">What Happens When You Withdraw</h4>
                           {withdrawalImplications.map((item, idx) => (
                              <div 
                                 key={idx}
                                 className={`p-4 rounded-lg border ${
                                    item.icon === 'warning' 
                                       ? 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200 dark:border-yellow-800' 
                                       : 'bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800'
                                 }`}
                              >
                                 <div className="flex items-start gap-3">
                                    {item.icon === 'warning' ? (
                                       <AlertTriangle className="w-5 h-5 text-yellow-600 dark:text-yellow-400 flex-shrink-0 mt-0.5" />
                                    ) : (
                                       <Info className="w-5 h-5 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5" />
                                    )}
                                    <div>
                                       <h5 className={`font-medium ${
                                          item.icon === 'warning' ? 'text-yellow-900 dark:text-yellow-300' : 'text-blue-900 dark:text-blue-300'
                                       }`}>{item.title}</h5>
                                       <p className={`text-sm mt-1 ${
                                          item.icon === 'warning' ? 'text-yellow-800 dark:text-yellow-300' : 'text-blue-800 dark:text-blue-300'
                                       }`}>{item.description}</p>
                                    </div>
                                 </div>
                              </div>
                           ))}
                        </div>

                        {/* Legal Notice */}
                        <div className="bg-gray-50 dark:bg-slate-800/50 border border-gray-200 dark:border-slate-700 rounded-lg p-4">
                           <p className="text-sm text-gray-600 dark:text-slate-300">
                              <strong>Legal Notice:</strong> Consent withdrawal takes effect within 24-48 hours. 
                              Certain disclosures required by law or for treatment, payment, and healthcare 
                              operations may continue as permitted under HIPAA.
                           </p>
                        </div>
                     </div>
                  )}

                  {/* Step 2: Confirmation with Reason */}
                  {withdrawStep === 2 && (
                     <div className="space-y-6 animate-fade-in">
                        {/* Reason Selection */}
                        <div>
                           <label className="block font-semibold text-gray-800 dark:text-slate-100 mb-3">
                              <MessageSquare className="w-4 h-4 inline-block mr-2" />
                              Why are you withdrawing this consent? (Optional)
                           </label>
                           <div className="space-y-2">
                              {withdrawalReasons.map((reason) => (
                                 <label
                                    key={reason.id}
                                    className={`flex items-center gap-3 p-3 rounded-lg border cursor-pointer transition-all ${
                                       withdrawReason === reason.id
                                          ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                          : 'border-gray-200 dark:border-slate-700 hover:border-gray-300 dark:hover:border-slate-600'
                                    }`}
                                 >
                                    <input
                                       type="radio"
                                       name="withdrawReason"
                                       value={reason.id}
                                       checked={withdrawReason === reason.id}
                                       onChange={(e) => setWithdrawReason(e.target.value)}
                                       className="text-blue-600"
                                    />
                                    <span className="text-gray-800 dark:text-slate-100">{reason.label}</span>
                                 </label>
                              ))}
                           </div>
                           
                           {/* Other reason text field */}
                           {withdrawReason === 'other' && (
                              <textarea
                                 value={withdrawReasonOther}
                                 onChange={(e) => setWithdrawReasonOther(e.target.value)}
                                 placeholder="Please describe your reason..."
                                 className="mt-3 w-full border border-gray-300 dark:border-slate-600 rounded-lg p-3 focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 focus:outline-none resize-none bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                                 rows={3}
                              />
                           )}
                        </div>

                        {/* Effective Date */}
                        <div>
                           <label className="block font-semibold text-gray-800 dark:text-slate-100 mb-3">
                              When should this withdrawal take effect?
                           </label>
                           <div className="space-y-2">
                              <label
                                 className={`flex items-center gap-3 p-3 rounded-lg border cursor-pointer transition-all ${
                                    withdrawEffective === 'immediate'
                                       ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                       : 'border-gray-200 dark:border-slate-700 hover:border-gray-300 dark:hover:border-slate-600'
                                 }`}
                              >
                                 <input
                                    type="radio"
                                    name="withdrawEffective"
                                    value="immediate"
                                    checked={withdrawEffective === 'immediate'}
                                    onChange={(e) => setWithdrawEffective(e.target.value)}
                                    className="text-blue-600"
                                 />
                                 <div>
                                    <span className="text-gray-800 dark:text-slate-100 font-medium">Immediately</span>
                                    <p className="text-sm text-gray-500 dark:text-slate-400">Takes effect within 24-48 hours</p>
                                 </div>
                              </label>
                           </div>
                        </div>

                        {/* Confirmation Text */}
                        <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
                           <label className="block font-semibold text-red-900 dark:text-red-300 mb-2">
                              To confirm withdrawal, type "WITHDRAW" below:
                           </label>
                           <input
                              type="text"
                              value={withdrawConfirmText}
                              onChange={(e) => setWithdrawConfirmText(e.target.value.toUpperCase())}
                              placeholder="Type WITHDRAW to confirm"
                              className="w-full border border-red-300 dark:border-red-700 rounded-lg px-4 py-2 focus:ring-2 focus:ring-red-500 focus:outline-none bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                           />
                        </div>

                        {/* Final Warning */}
                        <div className="flex items-start gap-3 p-4 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg">
                           <AlertTriangle className="w-5 h-5 text-yellow-600 dark:text-yellow-400 flex-shrink-0 mt-0.5" />
                           <p className="text-sm text-yellow-800 dark:text-yellow-300">
                              <strong>This action cannot be easily undone.</strong> While you can grant consent again 
                              in the future, any data sharing that was prevented during the withdrawal period cannot be recovered.
                           </p>
                        </div>
                     </div>
                  )}

                  {/* Step 3: Success Confirmation */}
                  {withdrawStep === 3 && (
                     <div className="text-center py-6 animate-fade-in">
                        {/* Success Icon */}
                        <div className="w-20 h-20 mx-auto bg-green-100 dark:bg-green-900/30 rounded-full flex items-center justify-center mb-6">
                           <CheckCircle className="w-10 h-10 text-green-600 dark:text-green-400" />
                        </div>
                        
                        <h3 className="text-xl font-bold text-gray-900 dark:text-slate-100 mb-2">Consent Successfully Withdrawn</h3>
                        <p className="text-gray-600 dark:text-slate-300 mb-6">
                           Your consent withdrawal request has been processed.
                        </p>

                        {/* Confirmation Details */}
                        <div className="bg-gray-50 dark:bg-slate-800/50 rounded-xl p-5 text-left mb-6">
                           <h4 className="font-semibold text-gray-800 dark:text-slate-100 mb-3">Withdrawal Details</h4>
                           <div className="space-y-2 text-sm">
                              <div className="flex justify-between">
                                 <span className="text-gray-500 dark:text-slate-400">Withdrawal ID:</span>
                                 <span className="font-mono font-medium text-gray-900 dark:text-slate-100">{withdrawConsentId}</span>
                              </div>
                              <div className="flex justify-between">
                                 <span className="text-gray-500 dark:text-slate-400">Consent Category:</span>
                                 <span className="font-medium text-gray-900 dark:text-slate-100">{formData.title}</span>
                              </div>
                              <div className="flex justify-between">
                                 <span className="text-gray-500 dark:text-slate-400">Effective:</span>
                                 <span className="font-medium text-gray-900 dark:text-slate-100">
                                    Immediately
                                 </span>
                              </div>
                              <div className="flex justify-between">
                                 <span className="text-gray-500 dark:text-slate-400">Submitted:</span>
                                 <span className="font-medium text-gray-900 dark:text-slate-100">{new Date().toLocaleString()}</span>
                              </div>
                              {withdrawReason && (
                                 <div className="flex justify-between">
                                    <span className="text-gray-500 dark:text-slate-400">Reason:</span>
                                    <span className="font-medium text-right max-w-xs text-gray-900 dark:text-slate-100">
                                       {withdrawReason === 'other' 
                                          ? withdrawReasonOther || 'Other' 
                                          : withdrawalReasons.find(r => r.id === withdrawReason)?.label}
                                    </span>
                                 </div>
                              )}
                           </div>
                        </div>

                        {/* What's Next */}
                        <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl p-5 text-left mb-6">
                           <h4 className="font-semibold text-blue-900 dark:text-blue-300 mb-2 flex items-center gap-2">
                              <Info className="w-4 h-4" /> What Happens Next
                           </h4>
                           <ul className="space-y-2 text-sm text-blue-800 dark:text-blue-300">
                              <li className="flex items-start gap-2">
                                 <Check className="w-4 h-4 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5" />
                                 <span>A confirmation email has been sent to {user?.email}</span>
                              </li>
                              <li className="flex items-start gap-2">
                                 <Check className="w-4 h-4 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5" />
                                 <span>All affected parties will be notified of this change</span>
                              </li>
                              <li className="flex items-start gap-2">
                                 <Check className="w-4 h-4 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5" />
                                 <span>Your consent status will be updated in our records</span>
                              </li>
                              <li className="flex items-start gap-2">
                                 <Check className="w-4 h-4 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5" />
                                 <span>You can re-authorize this consent at any time</span>
                              </li>
                           </ul>
                        </div>

                        {/* Download Confirmation */}
                        <IconButton 
                           icon={Download} 
                           label="Download Confirmation (PDF)" 
                           variant="outline" 
                           className="mb-4"
                        />
                     </div>
                  )}
               </div>

               {/* Modal Footer */}
               <div className="px-6 py-4 bg-gray-50 dark:bg-slate-800/50 border-t border-gray-100 dark:border-slate-700 flex justify-between gap-3">
                  {withdrawStep === 1 && (
                     <>
                        <Button 
                           variant="outline" 
                           onClick={() => setShowWithdrawModal(false)}
                        >
                           Cancel
                        </Button>
                        <Button 
                           className="bg-red-600 hover:bg-red-700"
                           onClick={() => setWithdrawStep(2)}
                        >
                           I Understand, Continue
                        </Button>
                     </>
                  )}
                  
                  {withdrawStep === 2 && (
                     <>
                        <Button 
                           variant="outline" 
                           onClick={() => setWithdrawStep(1)}
                        >
                           Go Back
                        </Button>
                        <Button 
                           className="bg-red-600 hover:bg-red-700"
                           onClick={handleWithdraw}
                           disabled={withdrawConfirmText !== 'WITHDRAW' || isWithdrawing}
                        >
                           {isWithdrawing ? (
                              <>
                                 <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                                 Processing...
                              </>
                           ) : (
                              <>
                                 <Trash2 className="w-4 h-4 mr-2" />
                                 Confirm Withdrawal
                              </>
                           )}
                        </Button>
                     </>
                  )}
                  
                  {withdrawStep === 3 && (
                     <Button 
                        className="w-full bg-brand-medium hover:bg-brand-deep"
                        onClick={() => {
                           setShowWithdrawModal(false);
                           onClose();
                        }}
                     >
                        Done
                     </Button>
                  )}
               </div>
            </div>
         </div>
      );
   }

   // Success Screen
   if (showSuccessScreen) {
      return (
         <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 dark:bg-black/70 backdrop-blur-sm">
            <div className="bg-white dark:bg-slate-800 rounded-2xl shadow-2xl max-w-lg w-full mx-4 p-8 text-center animate-fade-in">
               {/* Success Animation */}
               <div className="w-20 h-20 mx-auto bg-green-100 dark:bg-green-900/30 rounded-full flex items-center justify-center mb-6 animate-bounce">
                  <CheckCircle className="w-10 h-10 text-green-600 dark:text-green-400" />
               </div>
               
               <h2 className="text-2xl font-bold text-gray-900 dark:text-slate-100 mb-2">Consent Successfully Submitted!</h2>
               
               <div className="bg-gray-50 dark:bg-slate-800/50 rounded-lg p-4 mb-6 text-left">
                  <div className="space-y-2 text-sm">
                     <div className="flex justify-between">
                        <span className="text-gray-500 dark:text-slate-400">Consent ID:</span>
                        <span className="font-mono font-medium text-gray-900 dark:text-slate-100">{generatedConsentId}</span>
                     </div>
                     <div className="flex justify-between">
                        <span className="text-gray-500 dark:text-slate-400">Submitted on:</span>
                        <span className="font-medium text-gray-900 dark:text-slate-100">{new Date().toLocaleString()}</span>
                     </div>
                     <div className="flex justify-between">
                        <span className="text-gray-500 dark:text-slate-400">Effective date:</span>
                        <span className="font-medium text-gray-900 dark:text-slate-100">
                           {effectiveDate === 'immediate' ? 'Immediately' : customEffectiveDate}
                        </span>
                     </div>
                     <div className="flex justify-between">
                        <span className="text-gray-500 dark:text-slate-400">Status:</span>
                        <Badge type="green">Active</Badge>
                     </div>
                  </div>
               </div>

               <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4 mb-6 text-left">
                  <h3 className="font-semibold text-blue-900 dark:text-blue-300 mb-2">What happens next:</h3>
                  <ul className="space-y-1 text-sm text-blue-700 dark:text-blue-300">
                     <li>• Your consent has been recorded</li>
                     <li>• Confirmation email sent to {user?.email}</li>
                     <li>• Changes will take effect within 24-48 hours</li>
                  </ul>
               </div>

               <div className="flex flex-col gap-3">
                  <IconButton 
                     icon={Download} 
                     label="Download Consent PDF" 
                     variant="primary" 
                     className="w-full bg-brand-medium hover:bg-brand-deep"
                  />
                  <Button variant="outline" className="w-full" onClick={onClose}>
                     Return to Consent Dashboard
                  </Button>
               </div>

               <p className="text-sm text-gray-500 dark:text-slate-400 mt-4">
                  Redirecting in {countdown} seconds...
               </p>
            </div>
         </div>
      );
   }

   // Confirmation Modal
   if (showConfirmModal) {
      return (
         <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 dark:bg-black/70 backdrop-blur-sm">
            <div className="bg-white dark:bg-slate-800 rounded-2xl shadow-2xl max-w-lg w-full mx-4 max-h-[90vh] overflow-y-auto">
               <div className="p-6 border-b border-gray-100 dark:border-slate-700">
                  <div className="flex items-center gap-3">
                     <div className="w-12 h-12 bg-blue-100 dark:bg-blue-900/30 rounded-xl flex items-center justify-center">
                        <Shield className="w-6 h-6 text-blue-600 dark:text-blue-400" />
                     </div>
                     <div>
                        <h2 className="text-xl font-bold text-gray-900 dark:text-slate-100">Confirm Your Consent</h2>
                        <p className="text-sm text-gray-500 dark:text-slate-400">Review your selections before submitting</p>
                     </div>
                  </div>
               </div>

               <div className="p-6 space-y-4">
                  <div className="bg-gray-50 dark:bg-slate-800/50 rounded-lg p-4">
                     <p className="text-sm text-gray-500 dark:text-slate-400 mb-1">You are about to grant consent for:</p>
                     <p className="font-semibold text-gray-900 dark:text-slate-100">{formData.title}</p>
                     <p className="text-sm text-blue-600 dark:text-blue-400 mt-1">
                        {selectedOptions.length} option{selectedOptions.length !== 1 ? 's' : ''} selected
                     </p>
                  </div>

                  <div>
                     <p className="text-sm font-medium text-gray-700 dark:text-slate-200 mb-2">Selected options:</p>
                     <div className="max-h-40 overflow-y-auto space-y-1">
                        {formData.availableOptions
                           .filter(opt => selectedOptions.includes(opt.id))
                           .map(opt => (
                              <div key={opt.id} className="flex items-center gap-2 text-sm text-gray-600 dark:text-slate-300">
                                 <Check className="w-4 h-4 text-green-600 dark:text-green-400" />
                                 {opt.title}
                              </div>
                           ))}
                     </div>
                  </div>

                  <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-3">
                     <p className="text-sm text-yellow-800 dark:text-yellow-300 font-medium mb-1">Important Reminders:</p>
                     <ul className="text-sm text-yellow-700 dark:text-yellow-300 space-y-1">
                        <li>• This consent will be effective {effectiveDate === 'immediate' ? 'immediately' : `on ${customEffectiveDate}`}</li>
                        <li>• You can revoke this consent at any time</li>
                        <li>• You'll receive an email confirmation</li>
                     </ul>
                  </div>

                  <label className="flex items-start gap-3 p-3 bg-gray-50 dark:bg-slate-800/50 rounded-lg">
                     <input type="checkbox" className="mt-1" required />
                     <span className="text-sm text-gray-700 dark:text-slate-200">I confirm all the information above is correct</span>
                  </label>
               </div>

               <div className="p-6 bg-gray-50 dark:bg-slate-800/50 border-t border-gray-100 dark:border-slate-700 flex gap-3">
                  <Button 
                     variant="outline" 
                     className="flex-1"
                     onClick={() => setShowConfirmModal(false)}
                  >
                     Go Back to Edit
                  </Button>
                  <Button 
                     className="flex-1 bg-green-600 hover:bg-green-700"
                     onClick={handleSubmit}
                     disabled={isSubmitting}
                  >
                     {isSubmitting ? (
                        <>
                           <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                           Processing...
                        </>
                     ) : (
                        <>
                           <Check className="w-4 h-4 mr-2" />
                           Confirm & Submit
                        </>
                     )}
                  </Button>
               </div>
            </div>
         </div>
      );
   }

   return (
      <div className="fixed inset-0 z-50 flex">
         {/* Overlay */}
         <div 
            className="fixed inset-0 bg-black/50 dark:bg-black/70 backdrop-blur-sm"
            onClick={handleCancel}
         />
         
         {/* Slide-in Panel */}
         <div className="fixed right-0 top-0 bottom-0 w-full max-w-2xl bg-white dark:bg-slate-800 shadow-2xl flex flex-col animate-slide-in-right overflow-hidden">
            {/* Toast Notification */}
            {toast && (
               <div className={`absolute top-4 right-4 z-50 px-4 py-3 rounded-xl shadow-lg animate-fade-in flex items-center gap-2 ${
                  toast.type === 'success' ? 'bg-green-50 dark:bg-green-900/30 border border-green-200 dark:border-green-800 text-green-800 dark:text-green-300' :
                  toast.type === 'error' ? 'bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-800 text-red-800 dark:text-red-300' :
                  'bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-800 text-blue-800 dark:text-blue-300'
               }`}>
                  {toast.type === 'success' && <CheckCircle className="w-4 h-4 text-green-600 dark:text-green-400" />}
                  {toast.type === 'error' && <XCircle className="w-4 h-4 text-red-600 dark:text-red-400" />}
                  {toast.type === 'info' && <Info className="w-4 h-4 text-blue-600 dark:text-blue-400" />}
                  <span className="text-sm font-medium">{toast.message}</span>
               </div>
            )}

            {/* Header */}
            <div className="flex-shrink-0 px-6 py-4 border-b border-gray-100 dark:border-slate-700 bg-white dark:bg-slate-800">
               <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                     <button 
                        onClick={handleCancel}
                        className="p-2 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
                     >
                        <X className="w-5 h-5 text-gray-500 dark:text-slate-400" />
                     </button>
                     <div>
                        <h2 className="text-lg font-bold text-gray-900 dark:text-slate-100">
                           {mode === 'grant' ? 'Grant' : 'Modify'} Consent: {formData.title}
                        </h2>
                        <p className="text-sm text-gray-500 dark:text-slate-400">
                           Managing consents for: {user?.fullName || user?.email || 'Patient'}
                        </p>
                     </div>
                  </div>
                  <StepIndicator currentStep={step} totalSteps={3} />
               </div>
            </div>

            {/* Scrollable Content */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
               {/* Consent Category Information Card */}
               <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl p-5">
                  <div className="flex items-start gap-4">
                     <div className="w-12 h-12 bg-blue-100 dark:bg-blue-900/30 rounded-xl flex items-center justify-center flex-shrink-0">
                        <Shield className="w-6 h-6 text-blue-600 dark:text-blue-400" />
                     </div>
                     <div className="flex-1">
                        <h3 className="font-bold text-blue-900 dark:text-blue-300">{formData.title}</h3>
                        <p className="text-blue-700 dark:text-blue-300 mt-1">{formData.description}</p>
                        
                        <button
                           onClick={() => toggleSection('why-needed')}
                           className="mt-3 text-sm text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 flex items-center gap-1"
                        >
                           <Info className="w-4 h-4" />
                           Why we need this consent
                           {expandedSections['why-needed'] ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                        </button>
                        
                        {expandedSections['why-needed'] && (
                           <div className="mt-3 p-3 bg-white dark:bg-slate-800 rounded-lg text-sm text-blue-800 dark:text-blue-300 animate-fade-in">
                              {formData.whyNeeded}
                           </div>
                        )}

                        <button
                           onClick={() => toggleSection('learn-more')}
                           className="mt-2 text-sm text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300 flex items-center gap-1"
                        >
                           <HelpCircle className="w-4 h-4" />
                           Learn more
                           {expandedSections['learn-more'] ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                        </button>
                        
                        {expandedSections['learn-more'] && (
                           <div className="mt-3 p-3 bg-white dark:bg-slate-800 rounded-lg space-y-2 animate-fade-in">
                              {formData.learnMore.map((item, idx) => (
                                 <p key={idx} className="text-sm text-blue-800 dark:text-blue-300">• {item}</p>
                              ))}
                           </div>
                        )}
                     </div>
                  </div>
               </div>

               {/* Granular Consent Options Section */}
               <div>
                  <div className="flex items-center justify-between mb-4">
                     <div>
                        <h3 className="font-bold text-gray-900 dark:text-slate-100">What would you like to consent to?</h3>
                        <p className="text-sm text-gray-500 dark:text-slate-400">You can choose specific options below</p>
                     </div>
                     <div className="flex items-center gap-2">
                        <button
                           onClick={selectAll}
                           className="px-3 py-1.5 text-sm text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg flex items-center gap-1"
                        >
                           <CheckSquare className="w-4 h-4" />
                           Select All
                        </button>
                        <button
                           onClick={deselectAll}
                           className="px-3 py-1.5 text-sm text-gray-600 dark:text-slate-300 hover:bg-gray-100 dark:hover:bg-slate-700 rounded-lg flex items-center gap-1"
                        >
                           <Square className="w-4 h-4" />
                           Deselect All
                        </button>
                     </div>
                  </div>

                  <div className="flex items-center gap-2 mb-4 text-sm">
                     <span className="font-medium text-gray-700 dark:text-slate-200">
                        {selectedOptions.length} of {formData.availableOptions.length} selected
                     </span>
                  </div>

                  <div className="space-y-3">
                     {formData.availableOptions.map(option => (
                        <ConsentOptionCard
                           key={option.id}
                           option={option}
                           isSelected={selectedOptions.includes(option.id)}
                           onToggle={toggleOption}
                           isExpanded={expandedOptions[option.id]}
                           onToggleExpand={toggleExpandOption}
                           disabled={false}
                        />
                     ))}
                  </div>
               </div>

               {/* Effective Date Selector */}
               <div className="border-t border-gray-100 dark:border-slate-700 pt-6">
                  <h3 className="font-bold text-gray-900 dark:text-slate-100 mb-4">When should this consent take effect?</h3>
                  <div className="space-y-3">
                     <label className="flex items-center gap-3 p-3 border border-gray-200 dark:border-slate-700 rounded-lg cursor-pointer hover:bg-gray-50 dark:hover:bg-slate-700/50">
                        <input
                           type="radio"
                           name="effectiveDate"
                           checked={effectiveDate === 'immediate'}
                           onChange={() => setEffectiveDate('immediate')}
                           className="text-blue-600"
                        />
                        <div>
                           <span className="font-medium text-gray-900 dark:text-slate-100">Immediately upon submission</span>
                           <span className="text-sm text-gray-500 dark:text-slate-400 ml-2">(default)</span>
                        </div>
                     </label>
                     <label className="flex items-start gap-3 p-3 border border-gray-200 dark:border-slate-700 rounded-lg cursor-pointer hover:bg-gray-50 dark:hover:bg-slate-700/50">
                        <input
                           type="radio"
                           name="effectiveDate"
                           checked={effectiveDate === 'custom'}
                           onChange={() => setEffectiveDate('custom')}
                           className="mt-1 text-blue-600"
                        />
                        <div className="flex-1">
                           <span className="font-medium text-gray-900 dark:text-slate-100">On a specific date</span>
                           {effectiveDate === 'custom' && (
                              <input
                                 type="date"
                                 value={customEffectiveDate}
                                 onChange={(e) => setCustomEffectiveDate(e.target.value)}
                                 min={new Date().toISOString().split('T')[0]}
                                 className="mt-2 w-full px-3 py-2 border border-gray-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                              />
                           )}
                        </div>
                     </label>
                  </div>
                  {effectiveDate === 'custom' && (
                     <p className="text-sm text-gray-500 dark:text-slate-400 mt-2">
                        Changes scheduled for future dates can be cancelled before they take effect.
                     </p>
                  )}
               </div>

               {/* Expiration Settings */}
               <div className="border-t border-gray-100 dark:border-slate-700 pt-6">
                  <h3 className="font-bold text-gray-900 dark:text-slate-100 mb-4">How long should this consent remain active?</h3>
                  <div className="space-y-3">
                     {expirationOptions.map(option => (
                        <label 
                           key={option.value}
                           className="flex items-start gap-3 p-3 border border-gray-200 dark:border-slate-700 rounded-lg cursor-pointer hover:bg-gray-50 dark:hover:bg-slate-700/50"
                        >
                           <input
                              type="radio"
                              name="expiration"
                              checked={expiration === option.value}
                              onChange={() => setExpiration(option.value)}
                              className="mt-1 text-blue-600"
                           />
                           <div className="flex-1">
                              <span className="font-medium text-gray-900 dark:text-slate-100">{option.label}</span>
                              {expiration === 'custom' && option.value === 'custom' && (
                                 <input
                                    type="date"
                                    value={customExpirationDate}
                                    onChange={(e) => setCustomExpirationDate(e.target.value)}
                                    min={customEffectiveDate || new Date().toISOString().split('T')[0]}
                                    className="mt-2 w-full px-3 py-2 border border-gray-200 dark:border-slate-700 rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-slate-100"
                                 />
                              )}
                           </div>
                        </label>
                     ))}
                  </div>
                  <p className="text-sm text-gray-500 dark:text-slate-400 mt-2">
                     You can revoke this consent at any time, regardless of expiration.
                  </p>
               </div>

               {/* Privacy & Security Notices */}
               <div className="border-t border-gray-100 dark:border-slate-700 pt-6 space-y-4">
                  <h3 className="font-bold text-gray-900 dark:text-slate-100">Privacy & Security Information</h3>
                  
                  {/* Your Privacy Rights */}
                  <button
                     onClick={() => toggleSection('privacy-rights')}
                     className="w-full flex items-center justify-between p-4 bg-gray-50 dark:bg-slate-800/50 rounded-lg hover:bg-gray-100 dark:hover:bg-slate-700"
                  >
                     <span className="font-medium text-gray-800 dark:text-slate-100 flex items-center gap-2">
                        <Shield className="w-5 h-5 text-blue-600 dark:text-blue-400" />
                        Your Privacy Rights
                     </span>
                     {expandedSections['privacy-rights'] ? <ChevronUp className="w-5 h-5 text-gray-600 dark:text-slate-300" /> : <ChevronDown className="w-5 h-5 text-gray-600 dark:text-slate-300" />}
                  </button>
                  {expandedSections['privacy-rights'] && (
                     <ul className="px-4 space-y-2 animate-fade-in">
                        {privacyNotices.rights.map((right, idx) => (
                           <li key={idx} className="flex items-start gap-2 text-sm text-gray-600 dark:text-slate-300">
                              <Check className="w-4 h-4 text-green-600 dark:text-green-400 flex-shrink-0 mt-0.5" />
                              {right}
                           </li>
                        ))}
                     </ul>
                  )}

                  {/* How Your Information Is Protected */}
                  <button
                     onClick={() => toggleSection('protections')}
                     className="w-full flex items-center justify-between p-4 bg-gray-50 dark:bg-slate-800/50 rounded-lg hover:bg-gray-100 dark:hover:bg-slate-700"
                  >
                     <span className="font-medium text-gray-800 dark:text-slate-100 flex items-center gap-2">
                        <Lock className="w-5 h-5 text-blue-600 dark:text-blue-400" />
                        How Your Information Is Protected
                     </span>
                     {expandedSections['protections'] ? <ChevronUp className="w-5 h-5 text-gray-600 dark:text-slate-300" /> : <ChevronDown className="w-5 h-5 text-gray-600 dark:text-slate-300" />}
                  </button>
                  {expandedSections['protections'] && (
                     <ul className="px-4 space-y-2 animate-fade-in">
                        {privacyNotices.protections.map((item, idx) => (
                           <li key={idx} className="flex items-start gap-2 text-sm text-gray-600 dark:text-slate-300">
                              <Check className="w-4 h-4 text-green-600 dark:text-green-400 flex-shrink-0 mt-0.5" />
                              {item}
                           </li>
                        ))}
                     </ul>
                  )}

                  {/* What Happens Next */}
                  <button
                     onClick={() => toggleSection('next-steps')}
                     className="w-full flex items-center justify-between p-4 bg-gray-50 dark:bg-slate-800/50 rounded-lg hover:bg-gray-100 dark:hover:bg-slate-700"
                  >
                     <span className="font-medium text-gray-800 dark:text-slate-100 flex items-center gap-2">
                        <Clock className="w-5 h-5 text-blue-600 dark:text-blue-400" />
                        What Happens Next
                     </span>
                     {expandedSections['next-steps'] ? <ChevronUp className="w-5 h-5 text-gray-600 dark:text-slate-300" /> : <ChevronDown className="w-5 h-5 text-gray-600 dark:text-slate-300" />}
                  </button>
                  {expandedSections['next-steps'] && (
                     <ul className="px-4 space-y-2 animate-fade-in">
                        {privacyNotices.nextSteps.map((item, idx) => (
                           <li key={idx} className="flex items-start gap-2 text-sm text-gray-600 dark:text-slate-300">
                              <Check className="w-4 h-4 text-blue-600 dark:text-blue-400 flex-shrink-0 mt-0.5" />
                              {item}
                           </li>
                        ))}
                     </ul>
                  )}
               </div>

               {/* Confirmation Requirements */}
               <div className="border-t border-gray-100 dark:border-slate-700 pt-6">
                  <h3 className="font-bold text-gray-900 dark:text-slate-100 mb-4">Please confirm the following:</h3>
                  <div className="space-y-3">
                     {requiredAcknowledgments.map(ack => (
                        <label 
                           key={ack.id}
                           className={`flex items-start gap-3 p-4 border rounded-lg cursor-pointer transition-colors ${
                              acknowledgments[ack.id] 
                                 ? 'border-green-500 dark:border-green-700 bg-green-50 dark:bg-green-900/20' 
                                 : 'border-gray-200 dark:border-slate-700 hover:bg-gray-50 dark:hover:bg-slate-700/50'
                           }`}
                        >
                           <input
                              type="checkbox"
                              checked={acknowledgments[ack.id] || false}
                              onChange={() => toggleAcknowledgment(ack.id)}
                              className="mt-1 w-5 h-5 rounded text-blue-600"
                           />
                           <div className="flex-1">
                              <span className="text-gray-800 dark:text-slate-100">{ack.text}</span>
                              {ack.required && (
                                 <span className="text-red-500 ml-1">*</span>
                              )}
                              {ack.showsContact && acknowledgments[ack.id] && (
                                 <div className="mt-2 p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg text-sm">
                                    <p className="font-medium text-blue-800 dark:text-blue-300">Privacy Officer Contact:</p>
                                    <p className="text-blue-700 dark:text-blue-300">privacy@securehealth.example</p>
                                    <p className="text-blue-700 dark:text-blue-300">(555) 123-HIPAA</p>
                                 </div>
                              )}
                           </div>
                        </label>
                     ))}
                  </div>
               </div>

               {/* Digital Signature Section */}
               <div className="border-t border-gray-100 dark:border-slate-700 pt-6">
                  <h3 className="font-bold text-gray-900 dark:text-slate-100 mb-4">Your Signature</h3>
                  <SignaturePad
                     value={signatureValue}
                     onChange={setSignatureValue}
                     method={signatureMethod}
                     onMethodChange={setSignatureMethod}
                  />
                  <div className="mt-4 text-sm text-gray-500 dark:text-slate-400 flex items-center gap-4">
                     <div className="flex items-center gap-1">
                        <Wifi className="w-4 h-4" />
                        <span>IP: 192.168.1.100</span>
                     </div>
                     <div className="flex items-center gap-1">
                        <FileText className="w-4 h-4" />
                        <span>ID: {generatedConsentId || 'Will be assigned on submit'}</span>
                     </div>
                  </div>
               </div>

               {/* Consent History (for modify mode) */}
               {mode === 'modify' && (
                  <div className="border-t border-gray-100 dark:border-slate-700 pt-6">
                     <button
                        onClick={() => setShowHistory(!showHistory)}
                        className="w-full flex items-center justify-between p-4 bg-gray-50 dark:bg-slate-800/50 rounded-lg hover:bg-gray-100 dark:hover:bg-slate-700"
                     >
                        <span className="font-bold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                           <Clock className="w-5 h-5 text-gray-600 dark:text-slate-400" />
                           Consent Change History
                        </span>
                        {showHistory ? <ChevronUp className="w-5 h-5 text-gray-600 dark:text-slate-300" /> : <ChevronDown className="w-5 h-5 text-gray-600 dark:text-slate-300" />}
                     </button>
                     {showHistory && (
                        <div className="mt-4 animate-fade-in">
                           <div className="relative pl-4">
                              {patientHistory.map((item, idx) => (
                                 <HistoryTimelineItem 
                                    key={item.id} 
                                    item={item} 
                                    isLast={idx === patientHistory.length - 1}
                                 />
                              ))}
                           </div>
                           <div className="flex gap-2 mt-4">
                              <IconButton 
                                 icon={Download} 
                                 label="Export PDF" 
                                 variant="outline" 
                                 size="sm"
                              />
                              <IconButton 
                                 icon={Download} 
                                 label="Export CSV" 
                                 variant="outline" 
                                 size="sm"
                              />
                           </div>
                        </div>
                     )}
                  </div>
               )}
            </div>

            {/* Sticky Footer */}
            <div className="flex-shrink-0 px-6 py-4 bg-white dark:bg-slate-800 border-t border-gray-100 dark:border-slate-700 flex items-center justify-between gap-4">
               <div className="flex gap-3">
                  <Button 
                     variant="outline" 
                     className="text-gray-600 dark:text-slate-300"
                     onClick={handleCancel}
                  >
                     Cancel
                  </Button>
                  {mode === 'modify' && (
                     <Button 
                        variant="outline"
                        className="text-red-600 dark:text-red-400 border-red-300 dark:border-red-700 hover:bg-red-50 dark:hover:bg-red-900/20"
                        onClick={startWithdrawal}
                     >
                        <Trash2 className="w-4 h-4 mr-2" />
                        Withdraw Consent
                     </Button>
                  )}
               </div>
               <div className="relative group">
                  <Button 
                     className="bg-brand-medium hover:bg-brand-deep"
                     onClick={handleReview}
                     disabled={!isFormValid}
                  >
                     Review Consent
                  </Button>
                  {!isFormValid && (
                     <div className="absolute bottom-full right-0 mb-2 px-3 py-2 bg-gray-900 dark:bg-slate-700 text-white text-sm rounded-lg opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none">
                        {getValidationMessage()}
                     </div>
                  )}
               </div>
            </div>
         </div>
      </div>
   );
};

export default GrantModifyConsent;
